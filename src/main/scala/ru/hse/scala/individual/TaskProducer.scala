package ru.hse.scala.individual

import cats.effect._
import cats.effect.std.{Queue, Supervisor}
import cats.implicits._
import fs2.Stream

/** Читает поток строк, спавнит вычисления факториала и публикует результаты в очередь. */
final class TaskProducer[F[_]: Async](
    queue: Queue[F, ProcessMessage],
    workerSupervisor: Supervisor[F]
) {

  def run(stream: Stream[F, String]): F[Unit] =
    stream
      .takeWhile(line => line.trim != Task.exitCommand)
      .evalMap(text => spawnWorker(text.trim))
      .compile
      .drain

  private def spawnWorker(text: String): F[Unit] =
    workerSupervisor.supervise(
      FactorialAccumulator.inputNumber(text, queue)
    ).void
}

object TaskProducer {
  def make[F[_]: Async](
      queue: Queue[F, ProcessMessage],
      workerSupervisor: Supervisor[F]
  ): F[TaskProducer[F]] =
    Sync[F].pure(new TaskProducer[F](queue, workerSupervisor))
}
