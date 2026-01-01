package ru.hse.scala.individual.kafka

import pureconfig.ConfigReader
import pureconfig.ConfigSource
import pureconfig.generic.semiauto._

final case class KafkaConfig(
    bootstrapServers: String,
    groupId: String,
    inputTopic: String,
    outputTopic: String,
)

object KafkaConfig {
  implicit val configReader: ConfigReader[KafkaConfig] = deriveReader[KafkaConfig]

  def load(): Either[Throwable, KafkaConfig] =
    ConfigSource.default.at("kafka").load[KafkaConfig].left.map(errs => new RuntimeException(errs.prettyPrint()))

  def loadOrThrow(): KafkaConfig =
    load() match {
      case Right(cfg) => cfg
      case Left(err)  => throw err
    }
}



