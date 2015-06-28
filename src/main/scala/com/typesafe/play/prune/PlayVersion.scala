/*
 * Copyright (C) 2014 Typesafe Inc. <http://www.typesafe.com>
 */
package com.typesafe.play.prune

import java.nio.file._
import java.util.regex._

import scala.Predef

object PlayVersion {

  def readPlayVersionFromFile()(implicit ctx: Context): String = {
    val versionFilePath = Paths.get(ctx.playHome, "framework/version.sbt")
    val fileString: String = {
      val fileBytes = Files.readAllBytes(versionFilePath)
      new String(fileBytes, "UTF-8")
    }
    parsePlayVersion(fileString)
  }

  def parsePlayVersion(rawString: String): String = {
    val cleanedString = rawString.trim
    // Examples to match:
    // - Release.branchVersion in ThisBuild := "2.4-SNAPSHOT"
    // - version in ThisBuild := "2.5.0-SNAPSHOT"
    val pattern = Pattern.compile("(.*)ersion in ThisBuild := \"(.*)\"")
    val matcher = pattern.matcher(cleanedString)
    assert(matcher.matches(), s"Couldn't read Play version from [$cleanedString]: regex didn't match")
    val version = matcher.group(2)
    version
  }
}