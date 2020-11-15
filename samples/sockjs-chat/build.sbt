name := "sockjs-chat"

version := "0.1"

scalaVersion := "2.13.3"

enablePlugins(PlayScala)

libraryDependencies ++= Seq(
  guice,
  "com.github.fdimuccio" %% "play2-sockjs" % "0.8.1"
)
