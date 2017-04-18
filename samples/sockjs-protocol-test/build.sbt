name := "sockjs-protocol-test"

version := "0.1"

scalaVersion := "2.12.1"

enablePlugins(PlayScala, PlayNettyServer)
disablePlugins(PlayAkkaHttpServer)

resolvers += Resolver.sonatypeRepo("snapshots")

libraryDependencies ++= Seq(
  "com.github.fdimuccio" %% "play2-sockjs" % "0.6.0-SNAPSHOT",
  guice
)