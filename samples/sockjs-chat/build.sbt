name := "sockjs-chat"

version := "0.1"

scalaVersion := "2.11.7"

enablePlugins(PlayScala)

libraryDependencies ++= Seq(
  "com.softwaremill.macwire" %% "macros" % "2.2.3" % "provided",
  "com.github.fdimuccio" %% "play2-sockjs" % "0.5.1-SNAPSHOT"
)
