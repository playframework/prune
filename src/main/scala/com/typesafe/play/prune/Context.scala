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

  private def repoConfig(description: String, base: String): RepoConfig = {
    val nested = config.getConfig(base)
    RepoConfig(
      description = description,
      remote = nested.getString("remote"),
      branches = nested.getStringList("branches"),
      localDir = nested.getString("local-dir")
    )
  }

  val dbRepoConfig = repoConfig("database records", "db-repo")
  val playRepoConfig = repoConfig("Play source code", "play-repo")
  val testsRepoConfig = repoConfig("tests source code", "tests-repo")
  val playHome = playRepoConfig.localDir
  val testsHome = testsRepoConfig.localDir
}

case class Args(
  configFile: Option[String] = None,
  dbFetch: Boolean = true,
  playFetch: Boolean = true,
  testsFetch: Boolean = true,
  playRevision: Option[String] = None,
  testsRevision: Option[String] = None)


case class RepoConfig(
  description: String,
  remote: String,
  branches: JList[String],
  localDir: String
) {
  def localDirPath: Path = Paths.get(localDir)
  def mainBranch: String = branches.get(0)
  def mainBranchRef: String = "refs/heads/"+mainBranch

}
