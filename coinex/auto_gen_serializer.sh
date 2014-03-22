#!/bin/sh
exec scala "$0" "$@"
!#

import scala.util.matching.Regex
import java.io.File
import java.util.Calendar

val structNameExtractor = """\s+struct\s+(\w+)\s*\{""".r

val SERIALIZER_CODE_TEMPLATE = """
/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 *
 * This file was auto generated by auto_gen_serializer.sh on %s
 */

package com.coinport.coinex.serializers

import akka.serialization.Serializer
import com.twitter.bijection.scrooge.BinaryScalaCodec
import com.coinport.coinex.data._

class EventSerializer extends Serializer {
  val includeManifest: Boolean = true
  val identifier = 870725
%s

  def toBinary(obj: AnyRef): Array[Byte] = obj match {
%s

    case m => throw new IllegalArgumentException("Cannot serialize object: " + m)
  }

  def fromBinary(bytes: Array[Byte],
    clazz: Option[Class[_]]): AnyRef = clazz match {
%s

    case Some(c) => throw new IllegalArgumentException("Cannot deserialize class: " + c.getCanonicalName)
    case None => throw new IllegalArgumentException("No class found in EventSerializer when deserializing array: " + bytes.mkString(""))
  }
}
"""

val SERIALIZATION_CONFIG_TEMPLATE = """
#
# Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
#
# This file was auto generated by auto_gen_serializer.sh on %s

akka {
  actor {
    serializers {
      bytes = "akka.serialization.ByteArraySerializer"
      proto = "akka.remote.serialization.ProtobufSerializer"
      akka-containers = "akka.remote.serialization.MessageContainerSerializer"
      daemon-create = "akka.remote.serialization.DaemonMsgCreateSerializer"
      akka-cluster = "akka.cluster.protobuf.ClusterMessageSerializer"
      akka-pubsub = "akka.contrib.pattern.protobuf.DistributedPubSubMessageSerializer"
      akka-persistence-snapshot = "akka.persistence.serialization.SnapshotSerializer"
      akka-persistence-message = "akka.persistence.serialization.MessageSerializer"
      event = "com.coinport.coinex.serializers.EventSerializer"
    }
    serialization-bindings {
      "[B" = bytes
      "akka.event.Logging$LogEvent" = bytes
      "com.google.protobuf.GeneratedMessage" = proto
      "com.google.protobuf.Message" = proto
      "akka.actor.ActorSelectionMessage" = akka-containers
      "akka.remote.DaemonMsgCreate" = daemon-create
      "akka.cluster.ClusterMessage" = akka-cluster
      "akka.contrib.pattern.DistributedPubSubMessage" = akka-pubsub
      "akka.persistence.serialization.Snapshot" = akka-persistence-snapshot
      "akka.persistence.serialization.Message" = akka-persistence-message
      
%s
    }  
  }
}
"""

// Auto-generate EventSerializer code
def extractStructsFromFile(file: String): Seq[String] = {
  val lines = scala.io.Source.fromFile(file).mkString
  structNameExtractor.findAllIn(lines).matchData.map(_.group(1)).toSeq
}

def generateSerializerCode(structs: Seq[String], outputFile: String, time: String) = {
  val code = generateSerializerClassCode(
    time,
    structs.zipWithIndex.map { case (struct, idx) => generateCodecClauses(idx, struct) }.mkString,
    structs.zipWithIndex.map { case (struct, idx) => generateToBinaryClauses(idx, struct) }.mkString,
    structs.zipWithIndex.map { case (struct, idx) => generateFromBinaryClauses(idx, struct) }.mkString)
  val pw = new java.io.PrintWriter(new File(outputFile))
  try pw.write(code) finally pw.close()
}

def generateCodecClauses(idx: Int, struct: String) = "    val s_%d = BinaryScalaCodec(%s)\n".format(idx, struct)
def generateToBinaryClauses(idx: Int, struct: String) = "    case m: %s => s_%d(m)\n".format(struct, idx)
def generateFromBinaryClauses(idx: Int, struct: String) = "    case Some(c) if c == classOf[%s.Immutable] => s_%d.invert(bytes).get\n".format(struct, idx)
def generateSerializerClassCode(time: String, serializers: String, to: String, from: String) = SERIALIZER_CODE_TEMPLATE.format(time, serializers, to, from)

// Auto-generate Akka serialization configuration file
def generateStructSerializationConfigEntry(struct: String) = "      \"com.coinport.coinex.data.%s\" = event\n".format(struct)
def generateStructSerializationConfig(structs: Seq[String], outputFile: String, time: String) = {
  val configs = SERIALIZATION_CONFIG_TEMPLATE.format(time, structs.map(generateStructSerializationConfigEntry).mkString)
  val pw = new java.io.PrintWriter(new File(outputFile))
  try pw.write(configs) finally pw.close()
}

// Do the generation and replace existing files
val time = Calendar.getInstance().getTime().toString
val structs = extractStructsFromFile("coinex-client/src/main/thrift/messages.thrift")
generateSerializerCode(structs, "coinex-client/src/main/scala/com/coinport/coinex/serializers/EventSerializer.scala", time)
generateStructSerializationConfig(structs, "coinex-client/src/main/resources/serialization.conf", time)
