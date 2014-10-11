/*
 * Copyright (C) 2014 Typesafe Inc. <http://www.typesafe.com>
 */
package com.typesafe.play.prune

import java.nio.file._
import java.util.{ List => JList }
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib._
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.transport.{RemoteConfig, RefSpec}
import org.joda.time.{ReadableInstant, DateTime}
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
      Option(repository.resolve(fixedRev)).getOrElse(sys.error(s"Couldn't resolve revision $rev ($fixedRev) on branch $branch in repo $localDir"))
    }
  }

  private def refSpec(branch: String): RefSpec = {
    new RefSpec(s"refs/heads/$branch:refs/remotes/origin/$branch")
  }

  def gitCloneOrRebaseBranches(
    remote: String,
    localDir: String,
    branches: Seq[String],
    checkedOutBranch: Option[String]): Unit = {

    val branchesString = branches.mkString("[", ", ", "]")
    val branchToCheckout: String = checkedOutBranch.getOrElse(branches.head)
    val desc = s"$remote $branchesString into $localDir and checking out $branchToCheckout"

    val localDirPath = Paths.get(localDir)
    if (Files.notExists(localDirPath)) {
      println(s"Cloning $desc...")
      Files.createDirectories(localDirPath.getParent)
      Git.cloneRepository
        .setURI(remote)
        .setBranchesToClone(seqAsJavaList(branches))
        .setBranch(branchToCheckout)
        .setDirectory(localDirPath.toFile).call()
      println("Clone done.")
    } else {
      println(s"Pulling $desc...")
      withRepository(localDir) { repository =>
        validateRemoteOrigin(repository, remote)
        val git: Git = new Git(repository)

        {
          val existingBranches = asScalaBuffer(git.branchList.call()).map(_.getName.split('/').last)
          val missingBranches = branches diff existingBranches
          for (b <- missingBranches) {
            git.branchCreate.setName(b).setStartPoint(s"refs/remotes/origin/$b").call()
          }
        }
        {
          val refSpecs = branches.map(refSpec)
          git.fetch().setRemote("origin").setRefSpecs(refSpecs: _*).call()
        }
        val branchOperationOrder = branches.filter(_ != branchToCheckout) :+ branchToCheckout // Reorder so `branch` is checked out last
        for (b <- branchOperationOrder) {
          git.checkout().setName(b).call()
          val result = git.rebase().setUpstream(s"refs/remotes/origin/$b").call()
          println(s"Branch $b: ${result.getStatus}")
        }
      }
      println("Pull done")
    }

  }

  private def validateRemoteOrigin(repository: Repository, remote: String): Unit = {
    val config = repository.getConfig
    val remoteConfig = new RemoteConfig(config, "origin")
    val existingURIs = remoteConfig.getURIs
    assert(existingURIs.size == 1)
    val existingRemote = existingURIs.get(0).toString
    assert(existingRemote == remote, s"Remote URI for origin must be $remote, was $existingRemote")
  }

  def gitCheckout(localDir: String, branch: String, commit: String): Unit = {
    withRepository(localDir) { repository =>
      val result = new Git(repository).checkout().setName(branch).setStartPoint(commit).call()
    }
  }

  def gitPushChanges(
               remote: String,
               localDir: String,
               branch: String,
               commitMessage: String): Unit = {

    println(s"Pushing changes in $localDir to $remote branch $branch")

    withRepository(localDir) { repository =>
      val localGit: Git = new Git(repository)
      localGit.add.addFilepattern(".").call()
      //val result = localGit.push().
      val status = localGit.status.call()
      if (!status.getChanged.isEmpty) {
        localGit.commit.setAll(true).setMessage(commitMessage).call()
      }
      println(s"Pushing records to $remote [$branch]")
      val pushes = localGit.push.setRemote("origin").setRefSpecs(new RefSpec(s"$branch:$branch")).call()
      for {
        push <- iterableAsScalaIterable(pushes)
        remoteUpdate <- iterableAsScalaIterable(push.getRemoteUpdates)
      } {
        println(s"Pushed ${remoteUpdate.getSrcRef}: ${remoteUpdate.getStatus} ${remoteUpdate.getNewObjectId.name}")
      }
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

  def gitFirstParentsLog(localDir: String, branch: String, startRev: String, endRev: String): Seq[String] = {
    withRepository(localDir) { repository =>
      val startId: AnyObjectId = resolveId(localDir, branch, startRev)
      val endId: AnyObjectId = resolveId(localDir, branch, endRev)
      // println(s"Logging from $startId to $endId")

      val logWalk = new Git(repository).log().addRange(startId, endId).call()
      val iterator = logWalk.iterator()

      @scala.annotation.tailrec
      def walkBackwards(results: Seq[String], next: AnyObjectId): Seq[String] = {
        if (iterator.hasNext) {
          val commit = iterator.next()
          val current = commit.getId
          if (current == next) {
            // Stop walking because we've gone back before the end time
            walkBackwards(results :+ next.name, commit.getParent(0).getId)
          } else {
            // Skip this commit because its not the next commit that we're scanning for
            walkBackwards(results, next)
          }
        } else results
      }
      walkBackwards(Seq.empty, endId)
    }
  }
  def gitFirstParentsLogToDate(localDir: String, branch: String, lastRev: String, endTime: ReadableInstant): Seq[(String, DateTime)] = {
    withRepository(localDir) { repository =>
      val lastId: AnyObjectId = resolveId(localDir, branch, lastRev)
      val endTimeSeconds: Int = (endTime.getMillis / 1000).toInt
      // println(s"Logging from $startId to $endId")

      val logWalk = new Git(repository).log().add(lastId).call()
      val iterator = logWalk.iterator()

      @scala.annotation.tailrec
      def walkBackwards(results: Seq[(String, DateTime)], next: AnyObjectId): Seq[(String, DateTime)] = {
        if (iterator.hasNext) {
          val commit = iterator.next()
          val current = commit.getId
          if (current == next) {
            val commitTimeSeconds = commit.getCommitTime()
            val commitTime = new DateTime(commitTimeSeconds.toLong * 1000)
            val newResults: Seq[(String, DateTime)] = results :+(next.name, commitTime)
            if (commitTimeSeconds < endTimeSeconds) {
              // Stop walking because we've gone past the end time that we're interested in
              newResults
            } else {
              // Include this commit in our result and scan for its parent
              walkBackwards(newResults, commit.getParent(0).getId)
            }
          } else {
            // Skip this commit because its not the next commit that we're scanning for
            walkBackwards(results, next)
          }
        } else results
      }
      walkBackwards(Seq.empty, lastId)
    }
  }

}