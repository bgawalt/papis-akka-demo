name := "papis-akka-demo"

version := "master"

scalaVersion := "2.10.4"

libraryDependencies ++= Seq(
  "org.json4s" %% "json4s-native" % "3.2.10",
  "com.typesafe.akka" %% "akka-actor" % "2.3.6",
  "io.spray" %%  "spray-can"     % "1.3.2",
  "io.spray" %%  "spray-routing" % "1.3.2",
  "io.spray" %%  "spray-json" % "1.2.5"
)
