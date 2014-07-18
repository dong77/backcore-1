package com.coinport.bitway.NxtBitway.processor

import scala.util.Random
import com.coinport.bitway.NxtBitway.mongo.NxtMongoDAO
import com.coinport.bitway.NxtBitway.http.NxtHttpClient
import com.coinport.coinex.data._

/**
 * Created by chenxi on 7/18/14.
 */
class NxtProcessor(nxtMongo: NxtMongoDAO, nxtHttp: NxtHttpClient) {

  def getAddress(addressNum: Int) = {
    val secretSeq = generateSecret(addressNum)
    val nxts = nxtHttp.getMultiAddresses(secretSeq)
    nxtMongo.insertAddresses(nxts)

    val gar = GenerateAddressesResult(ErrorCode.Ok, Some(nxts.map(nxt => CryptoAddress(nxt.accountId, Option(nxt.secret))).toSet))
    BitwayMessage(
      currency = Currency.Nxt,
      generateAddressResponse = Some(gar)
    )
  }

  private def generateSecret(addressNum: Int): Seq[String] = {
    val rand = new Random()
    val count = nxtMongo.countAddress()
    (0 until addressNum).map{ i =>
      "www.coinport.com" + "%%%" + rand.nextString(10) +
        (count + i) + "%%%" + rand.nextString(10) +
        System.currentTimeMillis() + "%%%" + rand.nextString(10)
    }.toSeq
  }
}
