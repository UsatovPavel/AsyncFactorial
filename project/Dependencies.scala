import sbt.*

object Dependencies {

  val kindProjector = ("org.typelevel" %% "kind-projector"     % "0.13.4").cross(CrossVersion.full)
  val bmFor         = "com.olegpy"     %% "better-monadic-for" % "0.3.1"

  val catsEffect       = "org.typelevel" %% "cats-effect"       % "3.6.3"
  val scalaTest        = "org.scalatest" %% "scalatest"         % "3.2.19"
  val fs2Core          = "co.fs2"        %% "fs2-core"          % "3.7.0"
  val fs2Io            = "co.fs2"        %% "fs2-io"            % "3.7.0"
  val weaverCats       = "org.typelevel" %% "weaver-cats"       % "0.11.2"
  val weaverScalaCheck = "org.typelevel" %% "weaver-scalacheck" % "0.11.2"
}
