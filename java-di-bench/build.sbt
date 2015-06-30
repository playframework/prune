name := "java-bench"

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayJava)

libraryDependencies ++= Seq(
  json
)

javacOptions ++= Seq("-Xlint:deprecation")

scalaVersion := "2.10.5"

routesGenerator := InjectedRoutesGenerator
