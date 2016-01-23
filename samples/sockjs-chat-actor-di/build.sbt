version := "0.1"

scalaVersion := "2.11.7"

lazy val root = Project("sockjs-chat-actor-di", file("."))
  .enablePlugins(PlayScala)
  .dependsOn(ProjectRef(file("../.."), "play2-sockjs"))

libraryDependencies += "com.softwaremill.macwire" %% "macros" % "2.2.2" % "provided"

routesGenerator := InjectedRoutesGenerator