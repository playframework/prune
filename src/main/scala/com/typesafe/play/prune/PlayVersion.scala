/*
 * Copyright (C) 2014 Typesafe Inc. <http://www.typesafe.com>
 */
package com.typesafe.play.prune

import java.nio.file._
import java.util.regex._

object PlayVersion {

  def readPlayVersion()(implicit ctx: Context): String = {
    val versionFilePath = Paths.get(ctx.playHome, "framework/version.sbt")
    val fileString: String = {
      val fileBytes = Files.readAllBytes(versionFilePath)
      new String(fileBytes, "UTF-8").trim
    }
    // Examples to match:
    // - Release.branchVersion in ThisBuild := "2.4-SNAPSHOT"
    // - version in ThisBuild := "2.5.0-SNAPSHOT"
    val pattern = Pattern.compile("ersion in ThisBuild := \"(.*)\"")
    val matcher = pattern.matcher(fileString)
    assert(matcher.matches(), s"Couldn't read Play version from $versionFilePath: regex didn't match")
    val version = matcher.group(1)
    version
  }

}