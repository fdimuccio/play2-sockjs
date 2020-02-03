resolvers += "jgit-repo" at "https://download.eclipse.org/jgit/maven"

addSbtPlugin("org.xerial.sbt" % "sbt-sonatype" % "3.8.1")

addSbtPlugin("com.jsuereth" % "sbt-pgp" % "2.0.0")

addSbtPlugin("com.typesafe.sbt" % "sbt-git" % "1.0.0")
