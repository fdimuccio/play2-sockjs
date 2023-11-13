name := "sockjs-protocol-test"

version := "0.1"

scalaVersion := "2.13.12"

enablePlugins(PlayJava, PlayNettyServer)
disablePlugins(PlayPekkoHttpServer)

resolvers += Resolver.sonatypeRepo("snapshots")

libraryDependencies ++= Seq(
  guice,
  "com.github.fdimuccio" %% "play2-sockjs" % "0.10.0"
)

javaOptions += "-Xmx1G"

fork in run := true
