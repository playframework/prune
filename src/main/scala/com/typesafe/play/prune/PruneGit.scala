/*
 * Copyright (C) 2014 Typesafe Inc. <http://www.typesafe.com>
 */
package com.typesafe.play.prune

import java.io._
import java.nio.file._
import java.util.{ List => JList }
import java.util.concurrent.TimeUnit
import org.apache.commons.io.{ FileUtils, IOUtils }
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib._
import org.eclipse.jgit.revwalk._
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import scala.collection.JavaConversions

object PruneGit {

  def withRepository[T](repoConfig: RepoConfig)(f: Repository => T): T = {
    withRepository(repoConfig.localDir)(f)
  }

  def withRepository[T](localDir: String)(f: Repository => T): T = {
    val builder = new FileRepositoryBuilder()
    val repository = builder.setGitDir(Paths.get(localDir).resolve(".git").toFile)
            .readEnvironment() // Do we need this?
            .findGitDir()
            .build()
    val result = f(repository)
    repository.close()
    result
  }

  def gitSync(
    remote: String,
    localDir: String,
    branches: JList[String]): Unit = {

    println(s"Syncing $remote branches $branches into $localDir...")

    val localDirPath = Paths.get(localDir)
    if (Files.notExists(localDirPath)) {
      Files.createDirectories(localDirPath.getParent)
      println(s"Cloning...")
      Git.cloneRepository()
        .setURI(remote)
        .setBranchesToClone(branches)
        .setDirectory(localDirPath.toFile)
        .call()
      println("Clone done.")
    }

    println("Fetching...")
    withRepository(localDir) { repository =>
      val result = new Git(repository)
        .fetch()
        .call();
      // TODO: Add any missing branches, etc in case configuration
      // has changed since initial clone.
    }
    println("Fetch done")
  }

  def gitCheckout(repoConfig: RepoConfig, startPoint: String): Unit = {
    withRepository(repoConfig) { repository =>
      val result = new Git(repository).checkout().setName(repoConfig.mainBranchRef).setStartPoint(startPoint).call()
    }
  }

}