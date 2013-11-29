name := "dscript.play"

version := "1.0-SNAPSHOT"

resolvers += "nuvo.io maven repo" at "http://nuvo-io.github.com/mvn-repo/snapshots"

resolvers += "nuvo.io maven repo" at "http://nuvo-io.github.com/mvn-repo/releases"
    
resolvers += "Local Maven Repo" at "file://"+Path.userHome.absolutePath+"/.m2/repository"

libraryDependencies ++= Seq(
  "io.nuvo" % "moliere_2.10" % "0.1.1-SNAPSHOT",
  "io.nuvo" % "ishapes-typelib_2.10" % "1.0",
  "io.nuvo" % "plotty-typelib_2.10" % "1.0",
  "org.opensplice.mobile" % "ospl-mobile" % "1.1.0",
  "com.google.code.gson" % "gson" % "2.2.4",
  jdbc,
  anorm,
  cache
)     

play.Project.playScalaSettings
