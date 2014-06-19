import sbt._
import Keys._
import play.Project._

object ApplicationBuild extends Build {

    val appName         = "sockjs-chat"
    val appVersion      = "1.0"

    val main = play.Project(appName, appVersion).settings(
      libraryDependencies += "com.github.fdimuccio" %% "play2-sockjs" % "0.2.4",
      resolvers := Seq(
        "Maven2 Local" at new File(Path.userHome, ".m2/repository/snapshots").toURI.toURL.toExternalForm,
        Resolver.sonatypeRepo("snapshots")
      )
    )

}
