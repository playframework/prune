/*
 * Copyright (C) 2014 Typesafe Inc. <http://www.typesafe.com>
 */
package com.typesafe.play.prune

import play.api.libs.functional.syntax._
import play.api.libs.json.{JsPath, Reads, Json }


object Results {

  def parseWrkOutput(output: String): Either[String,WrkResult] = {
    val split: Array[String] = output.split("JSON:")
    split.tail.headOption.toRight("Missing JSON in result").right.flatMap { jsonText =>
      val json = Json.parse(jsonText)
      val result = WrkResult.reads.reads(json)
      result.fold(_ => Left(s"Failed to parse wrk result: $jsonText"), r => Right(r))
    }
  }

}

case class WrkResult(
  duration: Long, requests: Long, bytes: Long,
  connectErrors: Long, readErrors: Long, writeErrors: Long, statusErrors: Long,
  latency: Stats, requestsPerSecond: Stats
) {
  def summary: Either[String, WrkSummary] = {
    def errorMessage(errorCount: Long, errorName: String): Seq[String] = {
      if (errorCount == 0) Seq.empty else Seq(s"$errorName errors: $errorCount")
    }
    val errorMessages: Seq[String] =
      errorMessage(connectErrors, "Connect") ++ errorMessage(readErrors, "Read") ++
        errorMessage(writeErrors, "Write") ++ errorMessage(statusErrors, "Status")

    if (errorMessages.isEmpty) {
      Right(WrkSummary(
        requestsPerSecond = requests.toDouble / duration.toDouble * 1000000,
        latencyMean = latency.mean / 1000,
        latency95 = latency.percentiles(95).toDouble / 1000
      ))
    } else {
      Left(errorMessages.mkString(", "))
    }
  }
}

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

case class WrkSummary(
  requestsPerSecond: Double,
  latencyMean: Double,
  latency95: Double
) {
  def display: String = {
    s"Requests/s: ${requestsPerSecond}, "+
    s"Mean latency: ${latencyMean}, " +
    s"Latency 95%: ${latency95}"
  }
}
