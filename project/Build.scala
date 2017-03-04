import sbt._
import sbt.Keys._

object BuildSettings {
  val buildVersion = "0.6.0-SNAPSHOT"

  val buildSettings = Defaults.defaultSettings ++ Seq(
    organization := "com.github.fdimuccio",
    version := buildVersion,
    scalaVersion := "2.12.1",
    crossScalaVersions := Seq("2.11.8", "2.12.1"),
    crossVersion := CrossVersion.binary,
    javaOptions in Test ++= Seq("-Xmx1g", "-XX:MaxPermSize=512m"),
    javacOptions ++= Seq("-source", "1.8", "-target", "1.8", "-encoding", "UTF-8", "-Xlint:-options"),
    javacOptions in doc := Seq("-source", "1.8"),
    scalacOptions ++= Seq("-unchecked", "-deprecation"),
    scalacOptions in Test ++= Seq("-Yrangepos"),
    shellPrompt := ShellPrompt.buildShellPrompt,
    parallelExecution in Test := false,
    fork in Test := true
  ) ++ Publish.settings
}

object Publish {
  object TargetRepository {
    def local: Def.Initialize[Option[sbt.Resolver]] = version { (version: String) =>
      val localPublishRepo = Path.userHome.absolutePath + "/.m2/repository"
      if(version.trim.endsWith("SNAPSHOT"))
        Some(Resolver.file("snapshots", new File(localPublishRepo + "/snapshots")))
      else
        Some(Resolver.file("releases", new File(localPublishRepo + "/releases")))
    }
    def sonatype: Def.Initialize[Option[sbt.Resolver]] = version { (version: String) =>
      val nexus = "https://oss.sonatype.org/"
      if (version.trim.endsWith("SNAPSHOT"))
        Some("snapshots" at nexus + "content/repositories/snapshots")
      else
        Some("releases" at nexus + "service/local/staging/deploy/maven2")
    }
  }
  lazy val settings = Seq(
    publishMavenStyle := true,
    publishTo <<= TargetRepository.sonatype,
    publishArtifact in Test := false,
    pomIncludeRepository := { _ => false },
    licenses := Seq("Apache 2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
    homepage := Some(url("https://github.com/fdimuccio/play2-sockjs")),
    pomExtra :=
      <scm>
        <url>git@github.com:fdimuccio/play2-sockjs</url>
        <connection>scm:git:git@github.com:/play2-sockjs.git</connection>
      </scm>
      <developers>
        <developer>
          <id>fdimuccio</id>
          <name>Francesco Di Muccio</name>
          <url>https://github.com/fdimuccio</url>
        </developer>
      </developers>
  )
}

object ShellPrompt {
  object devnull extends ProcessLogger {
    def info(s: => String) {}

    def error(s: => String) {}

    def buffer[T](f: => T): T = f
  }

  def currBranch = (
    ("git status -sb" lines_! devnull headOption)
      getOrElse "-" stripPrefix "## ")

  val buildShellPrompt = (state: State) => {
    val currProject = Project.extract(state).currentProject.id
    "%s:%s:%s> ".format(currProject, currBranch, BuildSettings.buildVersion)
  }
}

object Play2SockJSBuild extends Build {
  import BuildSettings._

  val play2Version = "2.6.0-M1"

  lazy val play2SockJS = Project(
    "play2-sockjs",
    file("."),
    settings = buildSettings ++ Seq(
      resolvers := Seq(
        Resolver.sonatypeRepo("snapshots"),
        Resolver.sonatypeRepo("releases"),
        Resolver.typesafeRepo("snapshots"),
        Resolver.typesafeRepo("releases")
      ),
      libraryDependencies ++= Seq(
        "com.typesafe.play" %% "play" % play2Version % "provided->default",
        "com.typesafe.akka" %% "akka-http" % "10.0.4" % Test,
        "com.typesafe.akka" %% "akka-stream-testkit" % "2.4.17" % Test,
        "org.scalatestplus.play" %% "scalatestplus-play" % "2.0.0-M2" % Test,
        "com.typesafe.play" %% "play-netty-server" % play2Version % Test
      )
    )
  )
}
