name := "java-di-bench"

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayJava)

libraryDependencies ++= Seq(
  json
)

javacOptions ++= Seq("-Xlint:deprecation")

scalaVersion := "2.11.7"

routesGenerator := InjectedRoutesGenerator

resolvers += "scalaz-bintray" at "http://dl.bintray.com/scalaz/releases"
