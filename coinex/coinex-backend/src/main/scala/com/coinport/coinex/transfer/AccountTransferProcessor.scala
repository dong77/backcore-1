package com.coinport.coinex.transfer

import akka.actor._
import akka.event.LoggingReceive
import akka.event.LoggingAdapter
import akka.persistence._
import com.coinport.coinex.api.model.CurrencyWrapper
import com.coinport.coinex.common.{ ExtendedProcessor, Manager }
import com.coinport.coinex.common.PersistentId._
import com.coinport.coinex.common.support.ChannelSupport
import com.coinport.coinex.data._
import com.coinport.coinex.users.UserReader
import com.mongodb.casbah.Imports._
import com.twitter.util.Eval
import java.io.InputStream
import org.apache.commons.io.IOUtils
import scala.collection.mutable.Map

import ErrorCode._
import Implicits._
import TransferStatus._
import TransferType._

class AccountTransferProcessor(val db: MongoDB, accountProcessorPath: ActorPath, bitwayProcessors: collection.immutable.Map[Currency, ActorRef], mailer: ActorRef) extends ExtendedProcessor
    with EventsourcedProcessor with ChannelSupport with AccountTransferBehavior with ActorLogging {
  override val processorId = ACCOUNT_TRANSFER_PROCESSOR <<

  lazy implicit val logger: LoggingAdapter = log

  implicit val manager = new AccountTransferManager()
  val accountTransferConfig = loadConfig(context.system.settings.config.getString("akka.exchange.transfer-path"))
  val transferDebugConfig: Boolean = accountTransferConfig.transferDebug
  val transferConfig = TransferConfig(manualCurrency = Some(accountTransferConfig.manualCurrency))
  private val channelToAccountProcessor = createChannelTo(ACCOUNT_PROCESSOR <<) // DO NOT CHANGE
  private val bitwayChannels = bitwayProcessors.map(kv => kv._1 -> createChannelTo(BITWAY_PROCESSOR << kv._1))

  private val userReader = new UserReader(db)

  setSucceededRetainNum(accountTransferConfig.succeededRetainNum)
  intTransferHandlerObjectMap()

  private def loadConfig(configPath: String): AccountTransferConfig = {
    val in: InputStream = this.getClass.getClassLoader.getResourceAsStream(configPath)
    (new Eval()(IOUtils.toString(in))).asInstanceOf[AccountTransferConfig]
  }

  override def identifyChannel: PartialFunction[Any, String] = {
    case r: DoRequestTransfer =>
      r.transfer.`type` match {
        case HotToCold => "bitway_" + r.transfer.currency.toString.toLowerCase()
        case ColdToHot => "bitway_" + r.transfer.currency.toString.toLowerCase()
        case _ => "account"
      }
    case MultiCryptoCurrencyTransactionMessage(currency, _, _, _, _) => "bitway_" + currency.toString.toLowerCase()
  }

  def receiveRecover = PartialFunction.empty[Any, Unit]

  def receiveCommand = LoggingReceive {
    case p @ ConfirmablePersistent(DoRequestTransfer(w, _, _), _, _) =>
      confirm(p)
      val msg = DoRequestTransfer(w.copy(id = manager.getTransferId, updated = w.created), transferDebug = Some(transferDebugConfig), Some(transferConfig))
      if (isTransferByBitway(w.currency, msg.transferConfig) && !transferDebugConfig) {
        w.`type` match {
          case TransferType.Deposit =>
            sender ! RequestTransferFailed(UnsupportTransferType)
          case TransferType.UserToHot =>
            sender ! RequestTransferFailed(UnsupportTransferType)
          case TransferType.DepositHot =>
            sender ! RequestTransferFailed(UnsupportTransferType)
          case TransferType.Withdrawal => // accept wait for admin accept
            handleTransfer(msg)
            sender ! RequestTransferSucceeded(msg.transfer) // wait for admin confirm
          case TransferType.ColdToHot => // accept, wait for admin confirm
            handleTransfer(msg)
          case TransferType.HotToCold => // accept, save request to map
            persist(msg) {
              event =>
                updateState(event)
                sendBitwayMsg(msg.transfer.currency)
            }
          case TransferType.Unknown => // accept wait for admin accept
            handleTransfer(msg)
            sender ! RequestTransferFailed(UnsupportTransferType)
        }
      } else { // No need to send message, as accountProcessor will ignore it, just for integration test
        handleTransfer(msg)
        sender ! RequestTransferSucceeded(msg.transfer) // wait for admin confirm
      }

    case AdminConfirmTransferFailure(t, error) =>
      transferHandler.get(t.id) match {
        case Some(transfer) if transfer.status == Pending || transfer.status == Processing =>
          val updated = transfer.copy(updated = Some(System.currentTimeMillis), status = Rejected, reason = Some(error))
          persist(AdminConfirmTransferFailure(updated, error)) {
            event =>
              sender ! AdminCommandResult(Ok)
              deliverToAccountManager(event)
              updateState(event)
          }
        case Some(_) => sender ! AdminCommandResult(AlreadyConfirmed)
        case None => sender ! AdminCommandResult(TransferNotExist)
      }

    case DoCancelTransfer(t) =>
      transferHandler.get(t.id) match {
        case Some(transfer) if transfer.status == Pending =>
          if (t.userId == transfer.userId) {
            val updated = transfer.copy(updated = Some(System.currentTimeMillis), status = Cancelled, reason = Some(ErrorCode.UserCanceled))
            if (transfer.`type` == ColdToHot || transfer.`type` == Withdrawal) {
              persist(DoCancelTransfer(updated)) {
                event =>
                  sender ! AdminCommandResult(Ok)
                  deliverToAccountManager(event)
                  updateState(event)
              }
            } else {
              sender ! AdminCommandResult(UnsupportTransferType)
            }
          } else {
            sender ! AdminCommandResult(UserAuthenFail)
          }
        case Some(_) => sender ! AdminCommandResult(AlreadyConfirmed)
        case None => sender ! AdminCommandResult(TransferNotExist)
      }

    case AdminConfirmTransferSuccess(t, _, _) =>
      transferHandler.get(t.id) match {
        case Some(transfer) if transfer.status == Pending || transfer.status == HotInsufficient =>
          if (isTransferByBitway(transfer.currency, Some(transferConfig)) && !transferDebugConfig) {
            transfer.`type` match {
              case TransferType.Deposit => sender ! RequestTransferFailed(UnsupportTransferType)
              case TransferType.Withdrawal if transfer.address.isDefined =>
                val updated = transfer.copy(updated = Some(System.currentTimeMillis), status = Accepted)
                persist(AdminConfirmTransferSuccess(updated, Some(transferDebugConfig), Some(transferConfig))) {
                  event =>
                    updateState(event)
                    sendBitwayMsg(updated.currency)
                    sendWithdrawalNotification(transfer.userId, transfer.amount, transfer.currency)
                    sender ! AdminCommandResult(Ok)
                }
              case TransferType.ColdToHot =>
                val updated = transfer.copy(updated = Some(System.currentTimeMillis), status = Accepted)
                persist(AdminConfirmTransferSuccess(updated, Some(transferDebugConfig), Some(transferConfig))) {
                  event =>
                    updateState(event)
                    sender ! AdminCommandResult(Ok)
                }
              case _ =>
                sender ! RequestTransferFailed(UnsupportTransferType)
            }
          } else {
            transfer.`type` match {
              case TransferType.Withdrawal if !transferDebugConfig =>
                val updated = transfer.copy(updated = Some(System.currentTimeMillis), status = Processing)
                persist(AdminConfirmTransferSuccess(updated, Some(transferDebugConfig), Some(transferConfig))) {
                  event =>
                    updateState(event)
                    sendWithdrawalNotification(transfer.userId, transfer.amount, transfer.currency)
                }
              case _ =>
                val updated = transfer.copy(updated = Some(System.currentTimeMillis), status = Succeeded)
                persist(AdminConfirmTransferSuccess(updated, Some(transferDebugConfig), Some(transferConfig))) {
                  event =>
                    deliverToAccountManager(event)
                    updateState(event)
                    if (event.transfer.`type` == Withdrawal) {
                      sendWithdrawalNotification(transfer.userId, transfer.amount, transfer.currency)
                    }
                }
            }
            sender ! AdminCommandResult(Ok)
          }
        case Some(_) => sender ! AdminCommandResult(AlreadyConfirmed)
        case None => sender ! AdminCommandResult(TransferNotExist)
      }

    case AdminConfirmTransferProcessed(t) =>
      transferHandler.get(t.id) match {
        case Some(transfer) if transfer.status == Processing =>
          persist(AdminConfirmTransferProcessed(transfer.copy(updated = Some(System.currentTimeMillis), status = Succeeded))) {
            event =>
              deliverToAccountManager(event)
              updateState(event)
              sender ! AdminCommandResult(Ok)
          }
        case Some(_) => sender ! AdminCommandResult(AlreadyConfirmed)
        case None => sender ! AdminCommandResult(TransferNotExist)
      }

    case p @ ConfirmablePersistent(msg: MultiCryptoCurrencyTransactionMessage, _, _) =>
      persist(msg.copy(confirmNum = accountTransferConfig.confirmNumMap.get(msg.currency), timestamp = Some(System.currentTimeMillis()))) {
        event =>
          confirm(p)
          updateState(event)
          sendBitwayMsg(event.currency)
          sendAccountMsg(event.currency)
      }

    case msg: MultiCryptoCurrencyTransactionMessage =>
      persist(msg.copy(confirmNum = accountTransferConfig.confirmNumMap.get(msg.currency), timestamp = Some(System.currentTimeMillis()))) {
        event =>
          updateState(event)
          sendBitwayMsg(event.currency)
          sendAccountMsg(event.currency)
      }

    case tr: TransferCryptoCurrencyResult =>
      if (tr.error != ErrorCode.Ok && tr.request.isDefined && !tr.request.get.transferInfos.isEmpty) {
        persist(tr.copy(timestamp = Some(System.currentTimeMillis()))) {
          event =>
            updateState(event)
            sendBitwayMsg(event.currency)
            sendAccountMsg(event.currency)
        }
      }

    case mr: MultiTransferCryptoCurrencyResult =>
      if (mr.error != ErrorCode.Ok && mr.transferInfos.isDefined && !mr.transferInfos.get.isEmpty) {
        persist(mr.copy(timestamp = Some(System.currentTimeMillis()))) {
          event =>
            updateState(event)
            sendBitwayMsg(event.currency)
            sendAccountMsg(event.currency)
        }
      }
  }

  private def sendWithdrawalNotification(uid: Long, amount: Long, currency: Currency) = {
    val email = userReader.getEmailByUid(uid)
    val amountDouble = new CurrencyWrapper(amount).externalValue(currency).toString
    val content = "We are informing you that today, the amount of " + amountDouble + currency.toString +
      " has been drawn out of your account. "
    mailer ! DoSendEmail(email, EmailType.WithdrawalNotification, Map("CONTENT" -> content))
  }

  private def handleTransfer(msg: DoRequestTransfer) {
    persist(msg) {
      event =>
        updateState(event)
    }
  }

  private def sendBitwayMsg(currency: Currency) {
    val transfersToBitway = batchBitwayMessage(currency)
    if (!transfersToBitway.isEmpty) {
      deliverToBitwayProcessor(currency, MultiTransferCryptoCurrency(currency, transfersToBitway))
    }
  }

  private def sendAccountMsg(currency: Currency) {
    val transfersToAccount: Map[String, AccountTransfersWithMinerFee] = batchAccountMessage(currency)
    if (!transfersToAccount.isEmpty) {
      deliverToAccountManager(CryptoTransferResult(transfersToAccount))
    }
  }

  private def deliverToAccountManager(event: Any) = {
    log.info(s">>>>>>>>>>>>>>>>>>>>> deliverToAccountManager => event = ${event.toString}")
    if (!recoveryRunning) {
      channelToAccountProcessor ! Deliver(Persistent(event), accountProcessorPath)
    }
  }

  private def deliverToBitwayProcessor(currency: Currency, event: Any) = {
    log.info(s">>>>>>>>>>>>>>>>>>>>> deliverToBitwayProcessor => currency = ${currency.toString}, event = ${event.toString}, path = ${bitwayProcessors(currency).path.toString}")

    if (!recoveryRunning) {
      bitwayChannels(currency) ! Deliver(Persistent(event), bitwayProcessors(currency).path)
    }
  }
}

class AccountTransferManager() extends Manager[TAccountTransferState] {
  private var lastTransferId = 1E12.toLong
  private var lastTransferItemId = 6E12.toLong
  private var lastBlockHeight = Map.empty[Currency, Long]
  private val transferMapInnner = Map.empty[TransferType, Map[Long, CryptoCurrencyTransferItem]]
  private val succeededMapInnner = Map.empty[TransferType, Map[Long, CryptoCurrencyTransferItem]]
  private val sigId2MinerFeeMapInnner = Map.empty[TransferType, Map[String, Long]]
  private var transferHandlerObjectMap = Map.empty[TransferType, CryptoCurrencyTransferBase]

  def setTransferHandlers(transferHandlerObjectMap: Map[TransferType, CryptoCurrencyTransferBase]) {
    this.transferHandlerObjectMap = transferHandlerObjectMap
  }

  def getSnapshot = {
    transferMapInnner.clear()
    succeededMapInnner.clear()
    sigId2MinerFeeMapInnner.clear()
    transferHandlerObjectMap.keys foreach {
      txType =>
        val handler = transferHandlerObjectMap(txType)
        transferMapInnner.put(txType, handler.getTransferItemsMap())
        succeededMapInnner.put(txType, handler.getSucceededItemsMap())
        sigId2MinerFeeMapInnner.put(txType, handler.getSigId2MinerFeeMap())
    }
    TAccountTransferState(
      lastTransferId,
      lastTransferItemId,
      lastBlockHeight,
      transferMapInnner,
      succeededMapInnner,
      sigId2MinerFeeMapInnner,
      getFiltersSnapshot)
  }

  def loadSnapshot(s: TAccountTransferState) = {
    lastTransferId = s.lastTransferId
    lastTransferItemId = s.lastTransferItemId
    lastBlockHeight ++= s.lastBlockHeight
    transferHandlerObjectMap.keys foreach {
      txType =>
        val handler = transferHandlerObjectMap(txType)
        handler.loadSnapshotItems(s.transferMap(txType))
        handler.loadSnapshotSucceededItems(s.succeededMap(txType))
        handler.loadSigId2MinerFeeMap(s.sigId2MinerFeeMapInnner(txType))
    }
    loadFiltersSnapshot(s.filters)
  }

  def getTransferId = lastTransferId + 1

  def setLastTransferId(id: Long) = { lastTransferId = id }

  def getLastTransferItemId = lastTransferItemId

  def getNewTransferItemId = {
    lastTransferItemId += 1
    lastTransferItemId
  }

  def getLastBlockHeight(currency: Currency): Long = lastBlockHeight.getOrElse(currency, 0L)

  def setLastBlockHeight(currency: Currency, height: Long) = { lastBlockHeight.put(currency, height) }
}
