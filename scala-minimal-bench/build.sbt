import play.sbt.PlayFilters

name := "scala-minimal-bench"

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)
  .disablePlugins(PlayFilters)

resolvers += Resolver.sonatypeRepo("snapshots") 

scalaVersion := "2.12.1"
libraryDependencies += guice
