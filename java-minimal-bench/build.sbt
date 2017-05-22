name := "java-minimal-bench"

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayJava)
  .disablePlugins(PlayFilters)

resolvers += Resolver.sonatypeRepo("snapshots")

scalaVersion := "2.12.1"
javacOptions ++= Seq("-Xlint:deprecation")

libraryDependencies ++= Seq(
  guice,
  "com.typesafe.play" %% "play-json" % "2.6.0-M7"
)


