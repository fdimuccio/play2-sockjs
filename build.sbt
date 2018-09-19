version := "0.6.0"

name := "play2-sockjs"
organization := "com.github.fdimuccio"

scalaVersion := "2.12.6"
crossScalaVersions := Seq("2.11.12", "2.12.6")
crossVersion := CrossVersion.binary

javaOptions in Test ++= Seq("-Xmx1g", "-XX:MaxPermSize=512m")
javacOptions ++= Seq("-source", "1.8", "-target", "1.8", "-encoding", "UTF-8", "-Xlint:-options")
javacOptions in doc := Seq("-source", "1.8")

scalacOptions ++= Seq("-unchecked", "-deprecation")
scalacOptions in Test ++= Seq("-Yrangepos")

shellPrompt := ShellPrompt.buildShellPrompt(version.value)

parallelExecution in Test := false

fork in Test := true

publishMavenStyle := true
publishTo := sonatypePublishTo.value
publishArtifact in Test := false
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

val play2Version = "2.6.19"

libraryDependencies ++= Seq(
  "com.typesafe.play" %% "play" % play2Version % "provided->default",
  "com.typesafe.akka" %% "akka-http" % "10.1.5" % Test,
  "com.typesafe.akka" %% "akka-stream-testkit" % "2.5.16" % Test,
  "org.scalatest" %% "scalatest" % "3.0.3" % Test,
  "com.typesafe.play" %% "play-test" % play2Version % Test,
  "com.typesafe.play" %% "play-guice" % play2Version % Test,
  "com.typesafe.play" %% "play-netty-server" % play2Version % Test
)