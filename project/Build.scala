import sbt._
import Keys._
import play.Project._

object ApplicationBuild extends Build {

  val appName         = "dscript"
  val appVersion      = "1.0-SNAPSHOT"

  val appDependencies = Seq(
    "io.nuvo" % "moliere_2.10" % "0.1.0-SNAPSHOT",
    "org.opensplice.mobile" % "ospl-mobile" % "1.1.0",
    "com.google.code.gson" % "gson" % "2.2.4",
    jdbc,
    anorm
  )


  val main = play.Project(appName, appVersion, appDependencies).settings(
    resolvers += "nuvo.io maven repo" at "http://nuvo-io.github.com/mvn-repo/snapshots",
    resolvers += "Local Maven Repo" at "file://"+Path.userHome.absolutePath+"/.m2/repository"
  )

}
