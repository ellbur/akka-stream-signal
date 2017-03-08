
organization := "com.github.ellbur"

name := "akka-stream-signal"

version := "1.0.5"

scalaVersion := "2.12.1"

scalaSource in Compile := baseDirectory.value / "src"

javaSource in Compile := baseDirectory.value / "src"

scalaSource in Test := baseDirectory.value / "test"

javaSource in Test := baseDirectory.value / "test"

resourceDirectory in Compile := baseDirectory.value  / "resources"

resourceDirectory in Test := baseDirectory.value / "test-resources"

javacOptions ++= Seq("-encoding", "UTF-8")

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-stream" % "2.4.17"
)

fork := true

cancelable in Global := true

