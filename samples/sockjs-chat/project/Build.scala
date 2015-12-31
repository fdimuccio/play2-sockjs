import sbt._
import Keys._
import play.Play.autoImport._
import PlayKeys._

object ApplicationBuild extends Build {

  val appName         = "sockjs-chat"
  val appVersion      = "1.0"

  val main = Project(appName, file("."))
    .enablePlugins(play.sbt.PlayScala)
    .settings(
      scalaVersion := "2.11.7",
      version := appVersion
    ).dependsOn(ProjectRef(file("../.."), "play2-sockjs"))

}
