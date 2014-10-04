/*
 * Copyright (C) 2014 Typesafe Inc. <http://www.typesafe.com>
 */
package com.typesafe.play.prune

import com.typesafe.config.{ Config, ConfigFactory }
import java.io._
import java.nio.file._
import java.util.{ List => JList, Map => JMap, UUID }
import java.util.concurrent.TimeUnit
import org.apache.commons.io.{ FileUtils, IOUtils }
import org.apache.commons.exec._
import org.joda.time._
import scala.collection.convert.WrapAsScala._
import scala.concurrent._
import scala.concurrent.duration.Duration

case class Context(
  args: Args,
  config: Config
) {
  val pruneInstanceId = UUID.fromString(config.getString("pruneInstanceId"))
  val pruneHome = config.getString("home")
  val java8Home = config.getString("java8.home")
  val ivyHome = config.getString("ivy.home")

  val playRemote = config.getString("playRemote")
  val playHome = config.getString("playHome")

  val appsRemote = config.getString("appsRemote")
  val appsHome = config.getString("appsHome")

  val dbRemote = config.getString("dbRemote")
  val dbBranch = config.getString("dbBranch")
  val dbHome = config.getString("dbHome")

  val assetsHome = config.getString("assetsHome")

  val playTests: Seq[PlayTestsConfig] = {
    asScalaBuffer(config.getConfigList("playTests")).map { c: Config =>
      PlayTestsConfig(
        playBranch = c.getString("playBranch"),
        playRevisionRange = {
          val split = c.getString("playRevisionRange").split("\\.\\.")
          if (split.length != 2) {
            sys.error(s"Play revision range must contain a single '..': $split")
          }
          (split(0), split(1))
        },
        appsBranch = c.getString("appsBranch"),
        appsRevision = c.getString("appsRevision"),
        testNames = asScalaBuffer(c.getStringList("testNames"))
      )
    }
  }

//  private def getEntryNames(c: Config): Seq[String] = iterableAsScalaIterable(config.entrySet).to[Seq].foldLeft[Seq[String]](Seq.empty) {
//    case (names, entry) => names :+ entry.getKey
//  }

  val testConfig: Map[String, TestConfig] = {
    asScalaBuffer(config.getConfigList("tests")).foldLeft[Map[String, TestConfig]](Map.empty) {
      case (m, entry) =>
        val name: String = entry.getString("name")
        assert(!m.contains(name))
        m.updated(name, TestConfig(
        app = entry.getString("app"),
        description = entry.getString("description"),
        wrkArgs = asScalaBuffer(entry.getStringList("wrkArgs"))
      ))
    }
  }
}

case class Args(
  configFile: Option[String] = None,
  dbFetch: Boolean = true,
  dbPush: Boolean = false,
  playFetch: Boolean = true,
  appsFetch: Boolean = true,
  maxTestRuns: Option[Int] = None,
  maxWrkDuration: Option[Int] = None)

case class PlayTestsConfig(
  playBranch: String,
  playRevisionRange: (String, String),
  appsBranch: String,
  appsRevision: String,
  testNames: Seq[String]
)

case class TestConfig(
  app: String,
  description: String,
  wrkArgs: Seq[String]
)