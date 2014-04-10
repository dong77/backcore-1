package com.coinport.coinex.common

import akka.actor.Actor
import akka.persistence.Channel

trait ChannelSupport { self: Actor =>
  def processorId: String

  protected def createChannelTo(dest: String) = {
    val channelName = processorId + "_2_" + dest
    context.actorOf(Channel.props(channelName), channelName)
  }
}