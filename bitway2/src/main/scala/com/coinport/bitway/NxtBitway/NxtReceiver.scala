package com.coinport.bitway.NxtBitway

/**
 * Created by chenxi on 7/17/14.
 */
import akka.actor.{ActorLogging, Actor}
import akka.event.LoggingReceive
import scala.concurrent.duration._
import com.redis._
import com.redis.serialization.Parse.Implicits.parseByteArray
import com.coinport.coinex.serializers.ThriftBinarySerializer
import com.coinport.coinex.data.{BitwayMessage, BitwayRequest, Currency}
import com.coinport.coinex.data.BitwayRequestType._
import com.coinport.bitway.NxtBitway.processor.NxtProcessor

class NxtReceiver(processor: NxtProcessor, config: BitwayConfig) extends Actor with ActorLogging {
  val client = new RedisClient(config.ip, config.port)
  val serializer = new ThriftBinarySerializer()

  val responseChannel = config.responseChannelPrefix + Currency.Nxt.value.toString
  val requestChannel = config.requestChannelPrefix + Currency.Nxt.value.toString

  implicit val executeContext = context.system.dispatcher

  override def preStart = {
    super.preStart
    sendMessageToSelf(1)
  }

  def receive = LoggingReceive {
    case ListenAtRedis =>
      client.lpop(requestChannel) match {
        case Some(s) =>
          val request = serializer.fromBinary(s, classOf[BitwayRequest.Immutable]).asInstanceOf[BitwayRequest]
          val message: BitwayMessage = request.`type` match {
            case GenerateAddress =>
              processor.getAddress(request.generateAddresses.get.num)
            case MultiTransfer =>BitwayMessage(Currency.Nxt)
            case GetMissedBlocks =>BitwayMessage(Currency.Nxt)
            case SyncHotAddresses =>BitwayMessage(Currency.Nxt)
            case SyncPrivateKeys =>BitwayMessage(Currency.Nxt)
            case x => BitwayMessage(Currency.Nxt)
          }
          client.rpush(responseChannel, serializer.toBinary(message))
          sendMessageToSelf(0)
        case None =>
          sendMessageToSelf(5)
      }
  }

  private def sendMessageToSelf(timeout: Long = 0) {
    context.system.scheduler.scheduleOnce(timeout.seconds, self, ListenAtRedis)(context.system.dispatcher)
  }
}

