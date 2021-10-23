import Dependencies._

showCurrentGitBranch

git.useGitDescribe := true

lazy val commonSettings = Seq(
  organization := "org.hathitrust.htrc",
  organizationName := "HathiTrust Research Center",
  organizationHomepage := Some(url("https://www.hathitrust.org/htrc")),
  scalaVersion := "2.13.6",
  scalacOptions ++= Seq(
    "-feature",
    "-deprecation",
    "-language:postfixOps",
    "-language:implicitConversions"
  ),
  resolvers ++= Seq(
    Resolver.mavenLocal,
    "HTRC Nexus Repository" at "https://nexus.htrc.illinois.edu/repository/maven-public"
  ),
  Compile / packageBin / packageOptions += Package.ManifestAttributes(
    ("Git-Sha", git.gitHeadCommit.value.getOrElse("N/A")),
    ("Git-Branch", git.gitCurrentBranch.value),
    ("Git-Version", git.gitDescribedVersion.value.getOrElse("N/A")),
    ("Git-Dirty", git.gitUncommittedChanges.value.toString),
    ("Build-Date", new java.util.Date().toString)
  )
)

lazy val ammoniteSettings = Seq(
  libraryDependencies +=
    {
      val version = scalaBinaryVersion.value match {
        case "2.10" => "1.0.3"
        case _ ⇒  "2.4.0-23-76673f7f"
      }
      "com.lihaoyi" % "ammonite" % version % Test cross CrossVersion.full
    },
  Test / sourceGenerators += Def.task {
    val file = (Test / sourceManaged).value / "amm.scala"
    IO.write(file, """object amm extends App { ammonite.Main.main(args) }""")
    Seq(file)
  }.taskValue,
  connectInput := true,
  outputStrategy := Some(StdoutOutput)
)

lazy val `hathifiles-authortitle-match` = (project in file("."))
  .enablePlugins(GitVersioning, GitBranchPrompt, JavaAppPackaging)
  .settings(commonSettings)
  .settings(ammoniteSettings)
//  .settings(spark("3.2.0"))
  .settings(spark_dev("3.2.0"))
  .settings(
    name := "hathifiles-authortitle-match",
    description := "Searches the HathiFiles for volumes matching specified author/title pairs",
    licenses += "Apache2" -> url("http://www.apache.org/licenses/LICENSE-2.0"),
    maintainer := "capitanu@illinois.edu",
    libraryDependencies ++= Seq(
//      "me.xdrop"                      %  "fuzzywuzzy"               % "1.3.1",
      "org.hathitrust.htrc"           %% "scala-utils"              % "2.13",
      "org.hathitrust.htrc"           %% "spark-utils"              % "1.4",
      "org.rogach"                    %% "scallop"                  % "4.0.4",
      "com.github.nscala-time"        %% "nscala-time"              % "2.30.0",
      "ch.qos.logback"                %  "logback-classic"          % "1.2.6",
      "org.codehaus.janino"           %  "janino"                   % "3.0.8",  // versions > 3.0.8 are not working
      "org.scalacheck"                %% "scalacheck"               % "1.15.4"  % Test,
      "org.scalatest"                 %% "scalatest"                % "3.2.10"  % Test,
      "org.scalatestplus"             %% "scalacheck-1-15"          % "3.2.9.0" % Test
    ),
    dependencyOverrides ++= Seq(
      "com.google.guava" % "guava" % "15.0",
    ),
    Test / parallelExecution := false,
    Test / fork := true
  )
