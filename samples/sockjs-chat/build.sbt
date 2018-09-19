name := "sockjs-chat"

version := "0.1"

scalaVersion := "2.12.6"

enablePlugins(PlayScala)

resolvers += Resolver.sonatypeRepo("snapshots")

libraryDependencies ++= Seq(
  guice,
  "com.github.fdimuccio" %% "play2-sockjs" % "0.6.0"
)
