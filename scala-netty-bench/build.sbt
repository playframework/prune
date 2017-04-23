name := "scala-netty-bench"

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)
  .enablePlugins(PlayNettyServer)
  .disablePlugins(PlayAkkaHttpServer)


resolvers += Resolver.sonatypeRepo("snapshots") 

scalaVersion := "2.12.1"
libraryDependencies += guice
