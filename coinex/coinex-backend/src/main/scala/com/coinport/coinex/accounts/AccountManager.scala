/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 *
 *
 * MarketManager is the maintainer of a Market. It executes new orders before
 * they are added into a market as pending orders. As execution results, a list
 * of Transactions are generated and returned.
 *
 * MarketManager can be used by an Akka persistent processor or a view
 * to reflect pending orders and market depth.
 *
 * Note this class does NOT depend on event-sourcing framework we choose. Please
 * keep it plain old scala/java.
 */

package com.coinport.coinex.accounts

import scala.collection.mutable.Map
import com.coinport.coinex.data._
import com.coinport.coinex.common._
import Implicits._
import ErrorCode._
import com.sun.beans.decoder.FalseElementHandler

class AccountManager(initialLastOrderId: Long = 0L) extends Manager[TAccountState] {
  // Internal mutable state ----------------------------------------------
  private val accountMap: Map[Long, UserAccount] = Map.empty[Long, UserAccount]
  var aggregation = UserAccount(-1L, Map.empty[Currency, CashAccount])
  var lastOrderId = initialLastOrderId
  val abCodeMap: Map[Long, RCDItem] = Map.empty[Long, RCDItem]
  val codeAIndexMap: Map[String, Long] = Map.empty[String, Long]
  val codeBIndexMap: Map[String, Long] = Map.empty[String, Long]

  // Thrift conversions     ----------------------------------------------
  def getSnapshot = TAccountState(accountMap.clone, getFiltersSnapshot, aggregation, lastOrderId, abCodeMap, codeAIndexMap, codeBIndexMap)

  def loadSnapshot(snapshot: TAccountState) = {
    accountMap.clear
    accountMap ++= snapshot.userAccountsMap
    aggregation = snapshot.aggregation
    lastOrderId = snapshot.lastOrderId
    abCodeMap ++= snapshot.abCodeMap
    codeAIndexMap ++= snapshot.codeAIndexMap
    codeBIndexMap ++= snapshot.codeBIndexMap
    loadFiltersSnapshot(snapshot.filters)
  }

  // Business logics      ----------------------------------------------
  def transferFundFromLocked(from: Long, to: Long, currency: Currency, amount: Long) = {
    updateCashAccount(from, CashAccount(currency, 0, -amount, 0))
    updateCashAccount(to, CashAccount(currency, amount, 0, 0))
  }

  def transferFundFromAvailable(from: Long, to: Long, currency: Currency, amount: Long) = {
    updateCashAccount(from, CashAccount(currency, -amount, 0, 0))
    updateCashAccount(to, CashAccount(currency, amount, 0, 0))
  }

  def transferFundFromPendingWithdrawal(from: Long, to: Long, currency: Currency, amount: Long) = {
    updateCashAccount(from, CashAccount(currency, 0, 0, -amount))
    updateCashAccount(to, CashAccount(currency, amount, 0, 0))
  }

  def conditionalRefund(condition: Boolean)(currency: Currency, order: Order) = {
    if (condition && order.quantity > 0) refund(order.userId, currency, order.quantity)
  }

  def refund(uid: Long, currency: Currency, quantity: Long) = {
    updateCashAccount(uid, CashAccount(currency, quantity, -quantity, 0))
  }

  def canUpdateCashAccount(userId: Long, adjustment: CashAccount) = {
    val current = getUserCashAccount(userId, adjustment.currency)
    (current + adjustment).isValid
  }

  def updateCashAccount(userId: Long, adjustment: CashAccount) = {
    val current = getUserCashAccount(userId, adjustment.currency)
    val updated = current + adjustment
    assert(updated.isValid)
    setUserCashAccount(userId, updated)

    val updateAggregation = aggregation.cashAccounts.getOrElse(adjustment.currency, CashAccount(adjustment.currency, 0, 0, 0)) + adjustment
    assert(updateAggregation.isValid)
    aggregation = aggregation.copy(cashAccounts = aggregation.cashAccounts + (adjustment.currency -> updateAggregation))
  }

  def getUserAccounts(userId: Long): UserAccount =
    accountMap.get(userId).getOrElse(UserAccount(userId))

  private def getUserCashAccount(userId: Long, currency: Currency): CashAccount =
    getUserAccounts(userId).cashAccounts.getOrElse(currency, CashAccount(currency, 0, 0, 0))

  private def setUserCashAccount(userId: Long, cashAccount: CashAccount) = {
    if (!cashAccount.isValid)
      throw new IllegalArgumentException("Attempted to set user cash account to an invalid value: " + cashAccount)

    val accounts = accountMap.getOrElseUpdate(userId, UserAccount(userId))
    val updated = accounts.copy(cashAccounts = accounts.cashAccounts + (cashAccount.currency -> cashAccount))
    accountMap += userId -> updated
  }

  def getOrderId(): Long = lastOrderId + 1
  def setLastOrderId(id: Long) = { lastOrderId = id }

  def createABCodeTransaction(userId: Long, codeA: String, codeB: String, amount: Long) {
    val timestamp = System.currentTimeMillis
    val item = RCDItem(
      id = abCodeMap.size,
      wUserId = userId,
      codeA = codeA,
      codeB = codeB,
      status = RechargeCodeStatus.Unused,
      amount = amount,
      // default 1 year expiration interval
      rExpTime = Some(timestamp / 1000 + 365 * 86400),
      created = Some(timestamp),
      updated = Some(timestamp))
    abCodeMap += item.id -> item
    codeAIndexMap += codeA -> item.id
    codeBIndexMap += codeB -> item.id
  }

  def isCodeALocked(userId: Long, codeA: String): Boolean = {
    val now = System.currentTimeMillis / 1000
    abCodeMap.getOrElse(codeAIndexMap.getOrElse(codeA, -1), None) match {
      case i: RCDItem if i.status.getValue >= 2 => true
      case i: RCDItem if !i.qExpTime.isDefined || !i.dUserId.isDefined => false
      case i: RCDItem if now > i.qExpTime.get => false
      case i: RCDItem if now <= i.qExpTime.get &&
        i.dUserId.get == userId => false
      case _ => true
    }
  }

  def freezeABCode(userId: Long, codeA: String) {
    abCodeMap += abCodeMap(codeAIndexMap(codeA)).id ->
      abCodeMap(codeAIndexMap(codeA)).copy(dUserId = Some(userId),
        qExpTime = Some(System.currentTimeMillis / 1000 + 3600),
        status = RechargeCodeStatus.Frozen)
  }

  def verifyCodeB(userId: Long, codeB: String): (Boolean, Any) = {
    abCodeMap.getOrElse(codeBIndexMap.getOrElse(codeB, -1), None) match {
      case None => (false, InvalidBCode)
      case i: RCDItem if i.status.getValue >= 2 => (false, UsedBCode)
      case i: RCDItem if i.dUserId.isDefined &&
        userId != i.dUserId.get => (false, InvalidBCode)
      case i: RCDItem if i.rExpTime.isDefined &&
        i.rExpTime.get < System.currentTimeMillis / 1000 => (false, InvalidBCode)
      case _ => (true, None)
    }
  }

  def verifyConfirm(userId: Long, codeB: String): (Boolean, Any) = {
    println(abCodeMap.getOrElse(codeBIndexMap.getOrElse(codeB, -1), None))
    abCodeMap.getOrElse(codeBIndexMap.getOrElse(codeB, -1), None) match {
      case None => (false, InvalidBCode)
      case i: RCDItem if i.status.getValue >= 3 => (false, UsedBCode)
      case i: RCDItem if i.wUserId == userId &&
        i.rExpTime.get > System.currentTimeMillis / 1000 => (true, None)
      case _ => (false, InvalidBCode)
    }
  }

  def bCodeRecharge(userId: Long, codeB: String) =
    changeRechargeStatus(RechargeCodeStatus.Confirming, userId: Long, codeB: String)

  def confirmRecharge(userId: Long, codeB: String) =
    changeRechargeStatus(RechargeCodeStatus.RechargeDone, userId: Long, codeB: String)

  def changeRechargeStatus(rcStatus: RechargeCodeStatus, userId: Long, codeB: String) {
    abCodeMap += abCodeMap(codeBIndexMap(codeB)).id ->
      abCodeMap(codeBIndexMap(codeB))
      .copy(status = rcStatus, updated = Some(System.currentTimeMillis))
  }

  def getRCDepositRecords(userId: Long): Seq[RCDItem] = {
    abCodeMap.values.filter(_.dUserId.getOrElse(-1) == userId).toSeq.sortWith((a, b) => a.id > b.id)
  }

  def getRCWithdrawalRecords(userId: Long): Seq[RCDItem] = {
    abCodeMap.values.filter(_.wUserId == userId).toSeq.sortWith((a, b) => a.id > b.id)
  }

  def generateABCode(): (String, String) = {
    val random = new scala.util.Random
    def randStr(source: String)(n: Int): String =
      Stream.continually(random.nextInt(source.size)).map(source).take(n).mkString
    def randABCode(n: Int) =
      randStr("ABCDEF0123456789")(n)
    (randABCode(16), randABCode(32))
  }

}