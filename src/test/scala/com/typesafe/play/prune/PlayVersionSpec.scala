/*
 * Copyright (C) 2015 Typesafe Inc. <http://www.typesafe.com>
 */
package com.typesafe.play.prune

import org.specs2.mutable.Specification

object PlayVersionSpec extends Specification {

  "Play version passing" should {
    "read format 1" in {
      val versionString = """|Release.branchVersion in ThisBuild := "2.4.0-SNAPSHOT"
                             |""".stripMargin
      PlayVersion.parsePlayVersion(versionString) must_== "2.4.0-SNAPSHOT"
    }
    "read format 2" in {
      val versionString = """|version in ThisBuild := "2.5.0-SNAPSHOT"
                             |""".stripMargin
      PlayVersion.parsePlayVersion(versionString) must_== "2.5.0-SNAPSHOT"
    }
  }
}
