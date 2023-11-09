version := "0.9.0"

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

val play2Version = "2.9.0"
val akkaVersion = "2.6.21"
val akkaHttpVersion = "10.2.10"
val scalaTestVersion = "3.2.17"

libraryDependencies ++= Seq(
  "com.typesafe.play" %% "play" % play2Version % "provided->default",
  "com.typesafe.akka" %% "akka-http" % akkaHttpVersion % Test,
  "com.typesafe.akka" %% "akka-stream-testkit" % akkaVersion % Test,
  "org.scalatest" %% "scalatest" % scalaTestVersion % Test,
  "com.typesafe.play" %% "play-test" % play2Version % Test,
  "com.typesafe.play" %% "play-guice" % play2Version % Test,
  "com.typesafe.play" %% "play-netty-server" % play2Version % Test,
  "com.google.inject" % "guice" % "6.0.0" % "provided->default"
)
