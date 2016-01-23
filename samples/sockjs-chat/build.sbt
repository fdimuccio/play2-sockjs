version := "0.1"

scalaVersion := "2.11.7"

lazy val root = Project("sockjs-chat", file("."))
  .enablePlugins(PlayScala)
  .dependsOn(ProjectRef(file("../.."), "play2-sockjs"))