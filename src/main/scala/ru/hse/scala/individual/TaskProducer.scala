package ru.hse.scala.individual

import cats.effect._
import cats.effect.std.{Console, Queue, Supervisor}
import cats.implicits._

/** Читает пользовательский ввод, спавнит вычисления факториала и публикует результаты в очередь. */
final class TaskProducer[F[_]: Async: Console](
    queue: Queue[F, ProcessMessage],
    workerSupervisor: Supervisor[F],
    activeCount: Ref[F, Int]
) {

  private val prompt  = Task.prompt
  private val exitCmd = Task.exitCommand

  def run: F[Unit] = loop

  private def loop: F[Unit] =
    Console[F].println(prompt) *>
      Console[F].readLine.flatMap { text =>
        text.trim match {
          case t if t == exitCmd =>
            Console[F].println("Exit")
          case other =>
            spawnWorker(other) >> loop
        }
      }

  private def spawnWorker(text: String): F[Unit] =
    activeCount.update(_ + 1) *>
      workerSupervisor
        .supervise(
          FactorialAccumulator.inputNumber(text, queue)
            .guarantee(activeCount.update(_ - 1))
        )
        .void
}

object TaskProducer {
//Fiber в ресурсе new, лучше сделать приватным потому что легче менять TaskProducer
  def make[F[_]: Async: Console](
      queue: Queue[F, ProcessMessage],
      workerSupervisor: Supervisor[F],
      activeCount: Ref[F, Int]
  ): F[TaskProducer[F]] =
    Sync[F].pure(new TaskProducer[F](queue, workerSupervisor, activeCount))
}
