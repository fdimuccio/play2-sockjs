import sbt._
import Keys._
import play.Play.autoImport._
import PlayKeys._

object ApplicationBuild extends Build {

    val appName         = "sockjs-protocol-test"
    val appVersion      = "1.0"

    val main = Project(appName, file(".")).enablePlugins(play.PlayScala).settings(
      version := appVersion,
      resolvers += "Maven2 Local" at new File(Path.userHome, ".m2/repository/snapshots").toURI.toURL.toExternalForm,
      resolvers += Resolver.sonatypeRepo("snapshots"),
      libraryDependencies += "com.github.fdimuccio" %% "play2-sockjs" % "0.4.0-SNAPSHOT"
    )

}
