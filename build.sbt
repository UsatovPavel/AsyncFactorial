import org.typelevel.scalacoptions.{ScalaVersion, ScalacOption, ScalacOptions}
import sbt.addCompilerPlugin

import sbtassembly.AssemblyPlugin.autoImport._
import sbtassembly.MergeStrategy
import sbtassembly.PathList

ThisBuild / version                                       := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion                                  := "2.13.17"
ThisBuild / scalafixDependencies += "org.typelevel"       %% "typelevel-scalafix" % "0.5.0"
ThisBuild / scalafixDependencies += "com.github.vovapolu" %% "scaluzzi"           % "0.1.23"
ThisBuild / semanticdbEnabled                             := true
// explicitly set the version of semanticdb, because by default it's `4.9.9` which is not supports scala `2.13.17`
ThisBuild / semanticdbVersion := scalafixSemanticdb("4.14.1").revision

lazy val root: Project = (project in file("."))
  .settings(
    name := "individual-task",
    libraryDependencies ++= List(
      Dependencies.catsEffect,
      Dependencies.scalaTest % Test,
      Dependencies.fs2Core,
      Dependencies.fs2Io,
      Dependencies.fs2Kafka,
      // HTTP server 
      Dependencies.tapir.verxServer,
      Dependencies.tapir.cats,
      Dependencies.tapir.jsonTethys,
      Dependencies.tethys.jackson,
      Dependencies.tethys.derivation,
      // for local testing
      Dependencies.tapir.swagger,
    
      Dependencies.weaverCats       % Test,
      Dependencies.weaverScalaCheck % Test,
      // HTTP client for integration tests
      Dependencies.client.cats   % Test,
      Dependencies.client.tethys % Test,
      // JSON (tests only)
      Dependencies.circe.core    % Test,
      Dependencies.circe.parser  % Test,
      Dependencies.circe.generic % Test,
      // Config (server)
      Dependencies.pureconfig.core,
      Dependencies.pureconfig.generic,
    ),
    tpolecatExcludeOptions += ScalacOptions.warnNonUnitStatement,
    tpolecatScalacOptions += ScalacOption(
      "-Wconf:cat=lint-infer-any&msg=kind-polymorphic:s",
      _.isBetween(ScalaVersion.V2_13_0, ScalaVersion.V3_0_0),
    ),
    Compile / packageBin / mainClass := Some("ru.hse.scala.individual.HttpTask"),
    assembly / assemblyMergeStrategy := {
      // Swagger UI (official Tapir recommendation)
      case PathList("META-INF", "maven", "org.webjars", "swagger-ui", "pom.properties") => MergeStrategy.singleOrError
      case PathList("META-INF", "resources", "webjars", "swagger-ui", _ @ _*)           => MergeStrategy.singleOrError
      // Other META-INF
      case PathList("META-INF", "MANIFEST.MF")                        => MergeStrategy.discard
      case PathList("META-INF", "services", _ @ _*)                   => MergeStrategy.filterDistinctLines
      case PathList("META-INF", "versions", "9", "module-info.class") => MergeStrategy.discard
      case PathList("META-INF", "io.netty.versions.properties")       => MergeStrategy.first
      case PathList("META-INF", xs @ _*) if xs.nonEmpty               => MergeStrategy.discard
      case "reference.conf"                                           => MergeStrategy.concat
      case _                                                          => MergeStrategy.first
    },
    addCompilerPlugin(Dependencies.kindProjector),
    addCompilerPlugin(Dependencies.bmFor),
  )
