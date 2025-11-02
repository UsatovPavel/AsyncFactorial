import sbt.*

object Dependencies {

  val kindProjector = ("org.typelevel" %% "kind-projector"     % "0.13.4").cross(CrossVersion.full)
  val bmFor         = "com.olegpy"     %% "better-monadic-for" % "0.3.1"

  val catsEffect = "org.typelevel" %% "cats-effect" % "3.6.3"
}
