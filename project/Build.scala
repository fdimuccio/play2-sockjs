import sbt._
import sbt.Keys._

object BuildSettings {
  val buildVersion = "0.3.2-SNAPSHOT"

  val buildSettings = Defaults.defaultSettings ++ Seq(
    organization := "com.github.fdimuccio",
    version := buildVersion,
    scalaVersion := "2.10.4",
    crossScalaVersions := Seq("2.10.4", "2.11.4"),
    crossVersion := CrossVersion.binary,
    javaOptions in test ++= Seq("-Xmx512m", "-XX:MaxPermSize=512m"),
    javacOptions ++= Seq("-source", "1.6", "-target", "1.6", "-encoding", "UTF-8", "-Xlint:-options"),
    javacOptions in doc := Seq("-source", "1.6"),
    scalacOptions ++= Seq("-unchecked", "-deprecation"),
    shellPrompt := ShellPrompt.buildShellPrompt
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

  val buildShellPrompt = {
    (state: State) =>
    {
      val currProject = Project.extract(state).currentProject.id
      "%s:%s:%s> ".format(
        currProject, currBranch, BuildSettings.buildVersion)
    }
  }
}

object Play2SockJSBuild extends Build {
  import BuildSettings._

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
        "com.typesafe.akka" %% "akka-actor" % "2.3.6",
        "com.typesafe.play" %% "play" % "2.3.6" % "provided",
        "com.typesafe.play" %% "play-test" % "2.3.6" % "test",
        "com.typesafe.play" %% "play-ws" % "2.3.6" % "test",
        "org.specs2" %% "specs2" % "2.3.12" % "test",
        "junit" % "junit" % "4.8" % "test" cross CrossVersion.Disabled
      )
    )
  )
}
