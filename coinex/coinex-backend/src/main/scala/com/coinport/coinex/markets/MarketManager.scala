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

package com.coinport.coinex.markets

import com.coinport.coinex.data._
import com.coinport.coinex.data.mutable.MarketState
import com.coinport.coinex.common.Manager
import com.coinport.coinex.common.RedeliverFilter
import scala.annotation.tailrec
import scala.collection.mutable.ListBuffer
import Implicits._
import OrderStatus._
import RefundType._

class MarketManager(headSide: MarketSide) extends Manager[TMarketState] {

  var state = MarketState(headSide)

  val MAX_TX_GROUP_SIZE = 10000
  def isOrderPriceInGoodRange(takerSide: MarketSide, price: Option[Double]): Boolean = {
    if (price.isEmpty) true
    else if (price.get <= 0) false
    else if (state.priceRestriction.isEmpty || state.orderPool(takerSide).isEmpty) true
    else if (price.get / state.orderPool(takerSide).headOption.get.price.get - 1.0 <=
      state.priceRestriction.get) true
    else false
  }

  override def getSnapshot = state.toThrift.copy(filters = getFiltersSnapshot)

  override def loadSnapshot(s: TMarketState) {
    state = MarketState(s)
    loadFiltersSnapshot(s.filters)
  }

  private[markets] def apply(): MarketState = state.copy

  def orderExist(orderId: Long) = state.getOrder(orderId).isDefined

  def addOrder(takerSide: MarketSide, order: Order): OrderSubmitted = {
    val txsBuffer = new ListBuffer[Transaction]

    val (totalOutAmount, totalInAmount, takerOrder, newMarket) =
      addOrderRec(takerSide.reverse, takerSide, order, state, 0, 0, txsBuffer, order.id * MAX_TX_GROUP_SIZE)
    state = newMarket

    val status =
      if (takerOrder.isFullyExecuted) OrderStatus.FullyExecuted
      else if (totalOutAmount > 0) {
        if (takerOrder.price == None || takerOrder.onlyTaker.getOrElse(false)) OrderStatus.MarketAutoPartiallyCancelled
        else OrderStatus.PartiallyExecuted
      } else if (takerOrder.price == None || takerOrder.onlyTaker.getOrElse(false)) OrderStatus.MarketAutoCancelled
      else OrderStatus.Pending

    val refundType: Option[RefundType] = {
      if (takerOrder.quantity == 0) {
        None
      } else {
        if (takerOrder.hitTakeLimit) {
          Some(HitTakeLimit)
        } else if (takerOrder.isDust) {
          Some(Dust)
        } else if (status == MarketAutoPartiallyCancelled || status == MarketAutoCancelled) {
          Some(MarketCancelled)
        } else {
          None
        }
      }
    }

    if (txsBuffer.size != 0 && refundType != None) {
      val lastTx = txsBuffer.last
      lastTx.copy(takerUpdate = lastTx.takerUpdate.copy(current = lastTx.takerUpdate.current.copy(refund = refundType)))
      txsBuffer.trimEnd(1)
      txsBuffer += lastTx
    }

    val txs = txsBuffer.toSeq
    val orderInfo = OrderInfo(takerSide, if (txs.size == 0) order.copy(refund = refundType) else order,
      totalOutAmount, totalInAmount, status, txs.lastOption.map(_.timestamp))

    OrderSubmitted(orderInfo, txs)
  }

  def removeOrder(side: MarketSide, id: Long, userId: Long): Order = {
    val order = state.getOrder(id).get
    state = state.removeOrder(side, id)
    order
  }

  @tailrec
  private final def addOrderRec(makerSide: MarketSide, takerSide: MarketSide, takerOrder: Order,
    market: MarketState, totalOutAmount: Long, totalInAmount: Long, txsBuffer: ListBuffer[Transaction],
    txId: Long): ( /*totalOutAmount*/ Long, /*totalInAmount*/ Long, /*updatedTaker*/ Order, /*after order match*/ MarketState) = {
    val makerOrderOption = market.orderPool(makerSide).headOption
    if (makerOrderOption == None || makerOrderOption.get.vprice * takerOrder.vprice > 1) {
      // Return point. Market-price order doesn't pending
      (totalOutAmount, totalInAmount, takerOrder, if (!takerOrder.isFullyExecuted && takerOrder.price != None &&
        !takerOrder.onlyTaker.getOrElse(false)) market.addOrder(takerSide, takerOrder) else market)
    } else {
      val makerOrder = makerOrderOption.get
      val price = 1 / makerOrder.vprice
      val lvOutAmount = Math.min(takerOrder.maxOutAmount(price), makerOrder.maxInAmount(1 / price))
      if (lvOutAmount == 0) {
        // return point
        (totalOutAmount, totalInAmount, takerOrder, market)
      } else {
        val lvInAmount = Math.round(lvOutAmount * price)

        val updatedTaker = takerOrder.copy(quantity = takerOrder.quantity - lvOutAmount,
          takeLimit = takerOrder.takeLimit.map(_ - lvInAmount), inAmount = takerOrder.inAmount + lvInAmount)
        val updatedMaker = makerOrder.copy(quantity = makerOrder.quantity - lvInAmount,
          takeLimit = makerOrder.takeLimit.map(_ - lvOutAmount), inAmount = makerOrder.inAmount + lvOutAmount)
        val refundType: Option[RefundType] = if (updatedMaker.hitTakeLimit) {
          Some(HitTakeLimit)
        } else if (updatedMaker.isDust) {
          Some(Dust)
        } else {
          None
        }

        txsBuffer += Transaction(txId, takerOrder.timestamp.getOrElse(0), takerSide,
          takerOrder --> updatedTaker, makerOrder --> updatedMaker.copy(refund = refundType))

        val leftMarket = market.removeOrder(makerSide, makerOrder.id)
        if (updatedMaker.isFullyExecuted) {
          // return point
          addOrderRec(makerSide, takerSide, updatedTaker, leftMarket,
            totalOutAmount + lvOutAmount, totalInAmount + lvInAmount, txsBuffer, txId + 1)
        } else {
          // return point
          (totalOutAmount + lvOutAmount, totalInAmount + lvInAmount, updatedTaker,
            leftMarket.addOrder(makerSide, updatedMaker))
        }
      }
    }
  }
}
