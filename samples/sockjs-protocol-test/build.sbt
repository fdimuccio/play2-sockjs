name := "sockjs-protocol-test"

version := "0.1"

scalaVersion := "2.11.8"

enablePlugins(PlayScala)

//resolvers += Resolver.sonatypeRepo("snapshots")

libraryDependencies ++= Seq(
  "com.github.fdimuccio" %% "play2-sockjs" % "0.5.2"
)