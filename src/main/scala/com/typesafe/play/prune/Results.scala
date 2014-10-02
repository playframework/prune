/*
 * Copyright (C) 2014 Typesafe Inc. <http://www.typesafe.com>
 */
package com.typesafe.play.prune

import play.api.libs.functional.syntax._
import play.api.libs.json.{JsPath, Reads, Json }


object Results {

  def parseWrkOutput(output: String): Option[WrkResult] = {
    val split: Array[String] = output.split("JSON:")
    split.tail.headOption.map { jsonText =>
      //println(jsonText)
      val json = Json.parse(jsonText)
      val result = WrkResult.reads.reads(json)
      //println(result)
      result.get
    }
  }

}

case class WrkResult(
  duration: Long, requests: Long, bytes: Long,
  connectErrors: Long, readErrors: Long, writeErrors: Long, statusErrors: Long,
  latency: Stats, requestsPerSecond: Stats
)

object WrkResult {

  implicit val reads: Reads[WrkResult] = (
    (JsPath \ "summary" \ "duration").read[Long] and
    (JsPath \ "summary" \ "requests").read[Long] and
    (JsPath \ "summary" \ "bytes").read[Long] and
    (JsPath \ "summary" \ "errors" \ "connect").read[Long] and
    (JsPath \ "summary" \ "errors" \ "read").read[Long] and
    (JsPath \ "summary" \ "errors" \ "write").read[Long] and
    (JsPath \ "summary" \ "errors" \ "status").read[Long] and
    (JsPath \ "latency").read[Stats] and
    (JsPath \ "requests").read[Stats]
    )(WrkResult.apply _)
}

case class Stats(
  min: Long,
  mean: Double,
  max: Long,
  stdev: Double,
  percentiles: Map[Int, Long]
)

object Stats {

  implicit val reads: Reads[Stats] = (
    (JsPath \ "min").read[Long] and
    (JsPath \ "mean").read[Double] and
    (JsPath \ "max").read[Long] and
    (JsPath \ "stdev").read[Double] and
    (JsPath \ "percentile").read[Seq[Seq[Long]]].map { percentilePairArrays: Seq[Seq[Long]] =>
      percentilePairArrays.foldLeft[Map[Int,Long]](Map.empty) {
        case (percentiles, latencyPairArray) =>
          val percentile: Int = latencyPairArray(0).toInt
          val value: Long = latencyPairArray(1)
          percentiles.updated(percentile, value)
      }
    }
    )(Stats.apply _)
}