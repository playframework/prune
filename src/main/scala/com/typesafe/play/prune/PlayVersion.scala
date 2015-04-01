/*
 * Copyright (C) 2014 Typesafe Inc. <http://www.typesafe.com>
 */
package com.typesafe.play.prune

import java.nio.file._
import java.util.regex._

object PlayVersion {

  def readPlayVersion()(implicit ctx: Context): String = {
    val fileString: String = {
      val versionFilePath = Paths.get(ctx.playHome, "framework/version.sbt")
      val fileBytes = Files.readAllBytes(versionFilePath)
      new String(fileBytes, "UTF-8").trim
    }
    val pattern = Pattern.compile("Release.branchVersion in ThisBuild := \"(.*)\"")
    val matcher = pattern.matcher(fileString)
    assert(matcher.matches())
    val version = matcher.group(1)
    version
  }

}