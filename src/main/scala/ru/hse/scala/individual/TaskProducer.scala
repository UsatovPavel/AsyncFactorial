package ru.hse.scala.individual

import cats.effect._
import cats.effect.std.{Console, Queue, Supervisor}
import cats.implicits._
//можно представить как loop в Task, но вынесен в класс для расширяемости архитектуры
final class TaskProducer[F[_]: Async: Console](
    queue: Queue[F, ProcessMessage],
    workerSupervisor: Supervisor[F]
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
    workerSupervisor.supervise(
      FactorialAccumulator.inputNumber(text, queue)
    ).void
}

object TaskProducer {
//Fiber в ресурсе new, лучше сделать приватным потому что легче менять TaskProducer
  def make[F[_]: Async: Console](
      queue: Queue[F, ProcessMessage],
      workerSupervisor: Supervisor[F]
  ): F[TaskProducer[F]] =
    Sync[F].pure(new TaskProducer[F](queue, workerSupervisor))
}
