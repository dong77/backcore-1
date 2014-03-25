/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

package com.coinport.coinex.robot

import com.coinport.coinex.common.StateManager
import com.coinport.coinex.data._
import Implicits._

class RobotMetricsManager extends StateManager[RobotMetrics] {
  initWithDefaultState(RobotMetrics())

  def updatePrice(side: MarketSide, p: Double) {
    val metricsByMarket = state.marketByMetrics.get(side).getOrElse(MarketByMetrics(side, p))
    state = state.copy(marketByMetrics = state.marketByMetrics +
      (side -> metricsByMarket.copy(price = p),
        side.reverse -> metricsByMarket.copy(side = side.reverse, price = 1 / p)))
  }
}