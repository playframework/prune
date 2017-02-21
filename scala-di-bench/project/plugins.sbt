resolvers += Resolver.sonatypeRepo("snapshots")
val playVersion = sys.props.get("play.version").getOrElse(sys.error("Please set play.version system property"))
addSbtPlugin("com.typesafe.play" % "sbt-plugin" % playVersion)