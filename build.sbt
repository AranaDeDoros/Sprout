val scala3Version = "3.9.0"

lazy val root = project
  .in(file("."))
  .settings(
    name                                   := "sprout",
    version                                := "0.1.0-SNAPSHOT",
    scalaVersion                           := scala3Version,
    libraryDependencies += "org.scalameta" %% "munit"            % "1.3.6" % Test,
    libraryDependencies += "org.yaml"       % "snakeyaml"                 % "2.7",
    libraryDependencies += "org.scalameta" %% "scalafmt-dynamic"          % "3.11.5",
    libraryDependencies += "org.scala-lang.modules" % "scala-collection-compat_2.13" % "2.14.0",
    Test / fork := true,
    Test / classLoaderLayeringStrategy := ClassLoaderLayeringStrategy.Flat
  )
