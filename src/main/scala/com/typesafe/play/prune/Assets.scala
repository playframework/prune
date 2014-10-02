/*
 * Copyright (C) 2014 Typesafe Inc. <http://www.typesafe.com>
 */
package com.typesafe.play.prune

import java.nio.file._
import org.apache.commons.io.IOUtils

object Assets {
  def extractAssets(implicit ctx: Context): Unit = {
    Files.createDirectories(Paths.get(ctx.assetsHome))
    Seq("wrk_report.lua", "50k.bin") foreach { name =>
      val p = Paths.get(ctx.assetsHome, name)
      val r = getClass.getPackage.getName.replace(".", "/") + "/assets/" + name
      //println(s"Reading from $r")
      val bytes = IOUtils.toByteArray(this.getClass.getClassLoader.getResourceAsStream(r))
      Files.write(p, bytes)
    }
  }
}
