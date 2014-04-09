/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

package com.coinport.coinex.perf

import com.coinport.coinex.data.mutable.{ MarketState => MS }
import com.coinport.coinex.data.immutable.MarketState
import com.coinport.coinex.data.TMarketState
import com.coinport.coinex.data.Currency._
import com.coinport.coinex.data.Order
import com.coinport.coinex.data.Implicits._

object MarketStatePerf {
  def mutableTest() {
    val side = (Btc ~> Rmb)
    var num: Long = 4000 * 1000L
    var state = new MS(side)
    var start: Long = System.currentTimeMillis()
    for (i <- 0L until num) {
      state.addOrder(side, Order(i, i, 2000, Some(3429.0)))
    }
    var opsPerSecond: Long = (num * 1000L) / (System.currentTimeMillis() - start)
    var res: String = "The ops of add order for mutable state is %,d ops/sec" format opsPerSecond
    println(res)
    println(state.orderPool(side).size)

    num = 10
    var copyState: TMarketState = null
    start = System.currentTimeMillis()
    for (i <- 0L until num)
      copyState = state.toThrift

    opsPerSecond = (num * 1000L * 60L) / (System.currentTimeMillis() - start)
    res = "The ops of snapshot mutable state is %,d ops/min" format opsPerSecond
    println(res)
    println(copyState.orderPools(side).size)

    num = 10
    var newState: MS = null
    start = System.currentTimeMillis()
    for (i <- 0L until num)
      newState = MS(copyState)

    opsPerSecond = (num * 1000L * 60L) / (System.currentTimeMillis() - start)
    res = "The ops of reload mutable state from thrift is %,d ops/min" format opsPerSecond
    println(res)
    println(newState.orderPools(side).size)
  }

  def immutableTest() {
    val side = (Btc ~> Rmb)
    val num: Long = 4000 * 1000L
    var state = new MarketState(side)
    val start: Long = System.currentTimeMillis()
    for (i <- 0L until num) {
      state = state.addOrder(side, Order(i, i, 2000, Some(3429.0)))
    }
    val opsPerSecond: Long = (num * 1000L) / (System.currentTimeMillis() - start)
    val res: String = "The ops of add order for immutable state is %,d ops/sec" format opsPerSecond
    println(res)
    println(state.orderPool(side).size)
  }

  def main(args: Array[String]) {
    immutableTest()
    mutableTest()
  }
}
