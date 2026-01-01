package ru.hse.scala.individual.kafka

import cats.effect.{ExitCode, IO, IOApp, Resource}
import cats.syntax.all._
import fs2.kafka._
import ru.hse.scala.individual.http.{ProcessingEngine, ResultItemDto, ServerConfig, TaskItem}
import tethys._
import tethys.jackson._

/** Kafka consumer entrypoint.
  *
  * Consumes factorial tasks as JSON `TaskItem` from `kafka.inputTopic`,
  * computes a single `ResultItemDto`, publishes it to `kafka.outputTopic`,
  * then commits the offset (at-least-once).
  */
object KafkaConsumerTask extends IOApp {

  private def decodeTaskItem(payload: String): Either[Throwable, TaskItem] =
    payload.jsonAs[TaskItem].leftMap(err => new RuntimeException(err.toString))

  private def handleRecord(
      engine: ProcessingEngine[IO],
      producer: KafkaProducer.Metrics[IO, String, String],
      outputTopic: String,
  )(cr: CommittableConsumerRecord[IO, String, String]): IO[Unit] = {
    val rec = cr.record

    decodeTaskItem(rec.value) match {
      case Left(err) =>
        // Skip bad message (commit offset) so we don't get stuck.
        IO.println(s"[kafka-consumer] invalid TaskItem JSON, skipping. topic=${rec.topic} partition=${rec.partition} offset=${rec.offset} err=${err.getMessage}") *>
          cr.offset.commit

      case Right(item) =>
        for {
          // Engine works on lists; size=1 keeps implementation consistent across transports.
          res <- engine.run(List(item)).map(_.head)
          dto  = ResultItemDto.from(res)
          key  = item.jobId
          _ <- producer
            .produce(
              ProducerRecords.one(
                ProducerRecord(outputTopic, key, dto.asJson)
              )
            )
            .flatten
          _ <- cr.offset.commit
        } yield ()
    }
  }

  override def run(args: List[String]): IO[ExitCode] = {
    val serverCfg = ServerConfig.loadOrThrow()
    val kafkaCfg  = KafkaConfig.loadOrThrow()

    val startupLog =
      IO.println(
        s"[kafka-consumer] starting. bootstrap=${kafkaCfg.bootstrapServers} groupId=${kafkaCfg.groupId} in=${kafkaCfg.inputTopic} out=${kafkaCfg.outputTopic} parallelism=${serverCfg.parallelism}"
      )

    val consumerSettings =
      ConsumerSettings[IO, String, String]
        .withAutoOffsetReset(AutoOffsetReset.Earliest)
        .withBootstrapServers(kafkaCfg.bootstrapServers)
        .withGroupId(kafkaCfg.groupId)
        .withEnableAutoCommit(false)

    val producerSettings =
      ProducerSettings[IO, String, String]
        .withBootstrapServers(kafkaCfg.bootstrapServers)
        // strongest durability in the common case
        .withAcks(Acks.All)

    val app: Resource[IO, Unit] =
      for {
        engine   <- Resource.eval(ProcessingEngine.make[IO](serverCfg.parallelism))
        producer <- KafkaProducer.resource(producerSettings)
        _ <- Resource.eval(
          startupLog *>
          KafkaConsumer
            .stream(consumerSettings)
            .evalTap(_.subscribeTo(kafkaCfg.inputTopic))
            .flatMap(_.stream)
            // parallelize processing (each record produces + commits its own offset)
            .parEvalMapUnordered(serverCfg.parallelism.max(1))(
              handleRecord(engine, producer, kafkaCfg.outputTopic)
            )
            .compile
            .drain
        )
      } yield ()

    app.use(_ => IO.unit).as(ExitCode.Success)
  }
}


