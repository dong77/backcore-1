package com.coinport.bitway.NxtBitway

import scala.util.Random
import com.mongodb.casbah.MongoConnection
import akka.actor.{ActorSystem, Props}
import com.typesafe.config.ConfigFactory
import com.coinport.bitway.NxtBitway.mongo.NxtMongoDAO
import com.coinport.bitway.NxtBitway.http.NxtHttpClient
import com.coinport.bitway.NxtBitway.processor.NxtProcessor
import com.coinport.bitway.NxtBitway.actor.NxtActor

object NxtBitway {
  private val targetUrl="http://localhost:7876/nxt"
  private val mongocollection = MongoConnection("localhost", 27017)("coinex_bitway")("nxt")
  val mongoClient = new NxtMongoDAO(mongocollection)
  val httpClient = new NxtHttpClient(targetUrl)

  def start(): Unit = {
    val nxtProcessor = new NxtProcessor(mongoClient, httpClient)
    ActorSystem().actorOf(Props(new NxtActor(nxtProcessor, BitwayConfig())), name = "nxtReciever")
  }
}