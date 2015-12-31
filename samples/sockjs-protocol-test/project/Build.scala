import sbt._
import Keys._
import play.Play.autoImport._
import PlayKeys._

object ApplicationBuild extends Build {

    val appName         = "sockjs-protocol-test"
    val appVersion      = "1.0"

    val main = Project(appName, file("."))
      .enablePlugins(play.sbt.PlayScala)
      .settings(
        version := appVersion,
        scalaVersion := "2.11.7"
      ).dependsOn(ProjectRef(file("../.."), "play2-sockjs"))

}
