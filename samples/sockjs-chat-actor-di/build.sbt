name := "sockjs-chat-actor-di"

version := "0.1"

scalaVersion := "2.11.7"

enablePlugins(PlayScala)

libraryDependencies ++= Seq(
  "com.softwaremill.macwire" %% "macros" % "2.2.2" % "provided",
  "com.github.fdimuccio" %% "play2-sockjs" % "0.5.0"
)