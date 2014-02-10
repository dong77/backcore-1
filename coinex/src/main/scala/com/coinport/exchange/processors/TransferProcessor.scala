package com.coinport.exchange.processors

import com.typesafe.config.ConfigFactory
import akka.util.Timeout
import scala.concurrent.duration._
import akka.actor._
import akka.persistence._
import com.coinport.exchange.common._
import com.coinport.exchange.actors.LocalRouters
import scala.collection.mutable
import com.coinport.exchange.domain.Transfer
import com.coinport.exchange.domain.Commands._
import com.coinport.exchange.domain.Events._

/**
 * Transfer is either deposit or withdrawal.
 *
 * This processor holds all pending/cancelled/failed/confirmed transfers.
 * It sends events to another processor: BalanceProcessor to adjust balance.
 */
class TransferProcessor(routers: LocalRouters) extends EventsourcedProcessor with ActorLogging {
  override def processorId = "transfer_processor"

  // This is the channel from TransferProcessor to BalanceProcessor. It promises 2 things:
  // 1) all messages are delivered to the destination AT LEAST ONCE (probably multiple times)
  // 2) all messages are delivered in the order they are received, if one message is not confirmed
  // by the destination, younger messages will not be delivered.
  val balanceChannel = context.actorOf(PersistentChannel.props("transfer_2_balance_channel",
    PersistentChannelSettings(redeliverInterval = 3 seconds, redeliverMax = 15)),
    name = "transfer_2_balance_channel")

  // this `deposits` is the memory state
  val deposits = mutable.HashMap[Long, Transfer]()

  // Handles persistence events
  override def receiveRecover: Receive = {
    case e: Event => updateState(e)
    // case SnapshotOffer(_, snapshot: ExampleState) => state = snapshot
  }

  // Handles everything else.
  override def receiveCommand: Receive = {
    case DoCreatePendingDeposit(t) =>
      persist(PendingDepositCreated(t))(updateState)

    case DoConfirmDeposit(id) =>
      deposits.get(id) match {
        case Some(t) if t.status == "pending" =>
          persist(DepositConfirmed(t))(updateState)
        case _ =>
      }

    case DoCancelDeposit(id) =>
      deposits.get(id) match {
        case Some(t) if t.status == "pending" =>
          persist(DepositConcelled(t))(updateState(_))
        case _ =>
      }

    case cmd: DoCancelWithdrawal =>
    case cmd: DoConfirmWithdrawal =>
    case cmd: DoFailWithdrawal =>
  }

  private[this] def updateState(event: Event) = {
    event match {
      case PendingDepositCreated(t) =>
        val t2 = t.copy(id = lastSequenceNr, status = "pending")
        deposits += t2.id -> t2
        log.info("pending deposit: " + t2)

      case DepositConfirmed(t) =>
        val t2 = t.copy(status = "confirmed")
        deposits += t2.id -> t2
        balanceChannel ! Deliver(getCurrentPersistentMessage.withPayload(DepositConfirmed(t2)), routers.balanceProcessor.path)

      case DepositConcelled(t) =>
        val t2 = t.copy(status = "cancelled")
        deposits += t2.id -> t2

      case DepositFailed(t, reason) =>
        deposits.get(t.id) match {
          case Some(transfer) if transfer.status == "pending" =>
            val t2 = transfer.copy(status = "failed")
            deposits += t2.id -> t2
          case _ =>
        }

      case _ =>
    }
  }
}