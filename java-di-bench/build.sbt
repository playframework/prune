name := "java-di-bench"

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayJava)

libraryDependencies ++= Seq(
  guice,
  json
)

javacOptions ++= Seq("-Xlint:deprecation")

scalaVersion := "2.11.8"

routesGenerator := InjectedRoutesGenerator