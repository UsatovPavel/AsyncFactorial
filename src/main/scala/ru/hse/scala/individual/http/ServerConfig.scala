package ru.hse.scala.individual.http

import pureconfig.ConfigReader
import pureconfig.ConfigSource
import pureconfig.generic.semiauto._

final case class ServerConfig(
    host: String,
    port: Int,
    parallelism: Int,
)

object ServerConfig {
  implicit val configReader: ConfigReader[ServerConfig] = deriveReader[ServerConfig]

  private def normalize(cfg: ServerConfig): ServerConfig = {
    val p = cfg.parallelism match {
      case n if n > 0 => n
      case _          => Runtime.getRuntime.availableProcessors().max(1)
    }
    cfg.copy(parallelism = p)
  }

  def load(): Either[Throwable, ServerConfig] =
    ConfigSource.default.at("server").load[ServerConfig].left.map(errs => new RuntimeException(errs.prettyPrint()))
      .map(normalize)

  def loadOrThrow(): ServerConfig =
    load() match {
      case Right(cfg) => cfg
      case Left(err)  => throw err
    }
}
