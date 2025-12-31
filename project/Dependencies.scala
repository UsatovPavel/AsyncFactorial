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

  object tapir {
    // Keep versions aligned with `http-sample` to avoid binary incompatibilities.
    val version = "1.12.3"

    val verxServer = "com.softwaremill.sttp.tapir" %% "tapir-vertx-server-cats"  % version
    val cats       = "com.softwaremill.sttp.tapir" %% "tapir-cats"               % version
    val swagger    = "com.softwaremill.sttp.tapir" %% "tapir-swagger-ui-bundle"  % version
    val jsonTethys = "com.softwaremill.sttp.tapir" %% "tapir-json-tethys"        % version
  }

  object tethys {
    val version = "0.29.7"

    val jackson    = "com.tethys-json" %% "tethys-jackson213" % version
    val derivation = "com.tethys-json" %% "tethys-derivation" % version
  }

  object client {
    // sttp client4 (used in integration tests)
    val version = "4.0.13"

    val cats   = "com.softwaremill.sttp.client4" %% "cats"        % version
    val tethys = "com.softwaremill.sttp.client4" %% "tethys-json" % version
  }

  object circe {
    // Used only in tests to simplify JSON encoding/decoding.
    val version = "0.14.10"

    val core    = "io.circe" %% "circe-core"    % version
    val parser  = "io.circe" %% "circe-parser"  % version
    val generic = "io.circe" %% "circe-generic" % version
  }

  object pureconfig {
    val version = "0.17.9"

    val core    = "com.github.pureconfig" %% "pureconfig-core"    % version
    val generic = "com.github.pureconfig" %% "pureconfig-generic" % version
  }
}
