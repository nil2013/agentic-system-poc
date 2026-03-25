val scala3Version = "3.6.4"

lazy val root = project
  .in(file("."))
  .settings(
    name := "agentic-system-poc",
    version := "0.1.0-SNAPSHOT",
    scalaVersion := scala3Version,

    // HTTP client
    libraryDependencies += "com.softwaremill.sttp.client4" %% "core" % "4.0.19",
    libraryDependencies += "com.softwaremill.sttp.client4" %% "circe" % "4.0.19",

    // JSON
    libraryDependencies += "io.circe" %% "circe-generic" % "0.14.15",
    libraryDependencies += "io.circe" %% "circe-parser" % "0.14.15",

    // XML
    libraryDependencies += "org.scala-lang.modules" %% "scala-xml" % "2.3.0",

    // Test
    libraryDependencies += "org.scalameta" %% "munit" % "1.1.0" % Test,

    // Compiler options
    scalacOptions ++= Seq(
      "-deprecation",
      "-feature",
      "-unchecked",
    ),

    // REPL (Stage 8): sbt 経由で stdin を読むために必要
    run / fork := true,
    run / connectInput := true,
  )
