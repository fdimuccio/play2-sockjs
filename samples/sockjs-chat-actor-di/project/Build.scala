import play.routes.compiler.InjectedRoutesGenerator
import play.sbt.routes.RoutesKeys
import sbt._
import Keys._

object ApplicationBuild extends Build {

  val appName         = "sockjs-chat-actor-di"
  val appVersion      = "1.0"

  val main = Project(appName, file("."))
    .enablePlugins(play.sbt.PlayScala)
    .settings(
      scalaVersion := "2.11.7",
      version := appVersion,
      libraryDependencies += "com.softwaremill.macwire" %% "macros" % "2.2.2" % "provided",
      RoutesKeys.routesGenerator := InjectedRoutesGenerator
    ).dependsOn(ProjectRef(file("../.."), "play2-sockjs"))

}
