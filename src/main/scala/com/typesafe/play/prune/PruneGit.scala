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
import scala.collection.convert.WrapAsJava._
import scala.collection.convert.WrapAsScala._

object PruneGit {

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

  def resolveId(localDir: String, branch: String, rev: String): AnyObjectId = {
    withRepository(localDir) { repository =>
      val fixedRev = if (rev == "HEAD") s"refs/heads/$branch" else rev
      Option(repository.resolve(fixedRev)).getOrElse(sys.error(s"Couldn't resolve revision $rev on branch $branch in repo $localDir"))
    }
  }

  def gitSync(
    remote: String,
    localDir: String,
    branches: Seq[String]): Unit = {

    val branchesString = branches.mkString("[", ", ", "]")
    println(s"Syncing $remote branches $branchesString into $localDir...")

    val localDirPath = Paths.get(localDir)
    if (Files.notExists(localDirPath)) {
      Files.createDirectories(localDirPath.getParent)
      println(s"Cloning...")
      Git.cloneRepository()
        .setURI(remote)
        .setBranchesToClone(seqAsJavaList(branches))
        .setBranch(branches.head) // pick a random branch
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

  def gitCheckout(localDir: String, branch: String, commit: String): Unit = {
    withRepository(localDir) { repository =>
      val result = new Git(repository).checkout().setName(branch).setStartPoint(commit).call()
    }
  }

  case class LogEntry(id: String, parentCount: Int, shortMessage: String)

  def gitLog(localDir: String, branch: String, startRev: String, endRev: String): Seq[LogEntry] = {
    withRepository(localDir) { repository =>
      val startId = resolveId(localDir, branch, startRev)
      val endId = resolveId(localDir, branch, endRev)
      // println(s"Logging from $startId to $endId")
      val result = new Git(repository).log().addRange(startId, endId).call()
      val logEntries: Iterable[LogEntry] = iterableAsScalaIterable(result).map { revCommit =>
        //println(revCommit.getId.getName)
        LogEntry(revCommit.getId.name, revCommit.getParentCount, revCommit.getShortMessage)
      }
      logEntries.to[Seq]
    }
  }

}