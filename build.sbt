version := "0.10.0"

name := "play2-sockjs"
organization := "com.github.fdimuccio"

scalaVersion := "2.13.12"
crossScalaVersions := Seq("2.13.12")
crossVersion := CrossVersion.binary

Test / javaOptions ++= Seq("-Xmx1g")
javacOptions ++= Seq("--release", "11", "-encoding", "UTF-8", "-Xlint:-options")
doc / javacOptions := Seq("-source", "11")

scalacOptions ++= Seq("-unchecked", "-deprecation")
Test / scalacOptions ++= Seq("-Yrangepos")

shellPrompt := ShellPrompt.buildShellPrompt(version.value)

Test / parallelExecution := false

Test / fork := true

publishMavenStyle := true
publishTo := sonatypePublishToBundle.value
Test / publishArtifact := false
pomIncludeRepository := { _ => false }

licenses := Seq("Apache 2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0"))

homepage := Some(url("https://github.com/fdimuccio/play2-sockjs"))

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

val play3Version = "3.0.0"
val pekkoVersion = "1.0.1"
val pekkoHttpVersion = "1.0.0"
val scalaTestVersion = "3.2.17"

libraryDependencies ++= Seq(
  "org.playframework" %% "play" % play3Version % "provided->default",
  "org.apache.pekko" %% "pekko-http" % pekkoHttpVersion % Test,
  "org.apache.pekko" %% "pekko-stream-testkit" % pekkoVersion % Test,
  "org.scalatest" %% "scalatest" % scalaTestVersion % Test,
  "org.playframework" %% "play-test" % play3Version % Test,
  "org.playframework" %% "play-guice" % play3Version % Test,
  "org.playframework" %% "play-netty-server" % play3Version % Test,
  "com.google.inject" % "guice" % "6.0.0" % "provided->default"
)
