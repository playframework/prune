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
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib._
import org.eclipse.jgit.revwalk._
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.joda.time._
import scala.collection.JavaConversions
import scala.concurrent._
import scala.concurrent.duration.Duration
import scala.concurrent.ExecutionContext.Implicits.global
import scopt.OptionParser

import Exec._

object JavaVersion {

  def captureJavaVersion()(implicit ctx: Context): Execution = {
    run(
      Command(
        program = "<java8.home>/bin/java",
        args = Seq("-version"),
        workingDir = "<prune.home>",
        env = Map()
      ),
      streamHandling = Capture,
      errorOnNonZeroExit = false,
      timeout = Some(60000)
    )
  }

}