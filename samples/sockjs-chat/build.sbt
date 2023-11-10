name := "sockjs-chat"

version := "0.1"

scalaVersion := "2.13.12"

enablePlugins(PlayScala)

libraryDependencies ++= Seq(
  guice,
  "com.github.fdimuccio" %% "play2-sockjs" % "0.10.0"
)
