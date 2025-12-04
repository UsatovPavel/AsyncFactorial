package ru.hse.scala.individual

import cats.effect._
import cats.effect.std.{Console, Queue, Supervisor}
import cats.implicits._
//можно представить как loop в Task, но вынесен в класс для расширяемости архитектуры
final class TaskProducer[F[_]: Async: Console](
    queue: Queue[F, ProcessMessage],
    supervisor: Supervisor[F],
    waitGroup: WaitGroup[F],
    waitForAll: Boolean
) {

  private val prompt = "Enter number:"
  private val exit   = "exit"

  def run: F[Unit] = loop

  private def loop: F[Unit] =
    Console[F].println(prompt) *>
      Console[F].readLine.flatMap { text =>
        if (text.trim == exit) handleExit
        else spawnTask(text) *> loop
      }

  private def spawnTask(text: String): F[Unit] =
    for {
      fiber <- supervisor.supervise(
        FactorialAccumulator.inputNumber(text, queue)
      )
      _ <- waitGroup.register(fiber)
    } yield ()

  private def handleExit: F[Unit] =
    for {
      _ <- if (waitForAll) waitGroup.await else Async[F].unit
      _ <- Console[F].println("Exit")
    } yield ()
}

object TaskProducer {
  def make[F[_]: Async: Console](
      queue: Queue[F, ProcessMessage],
      supervisor: Supervisor[F],
      waitGroup: WaitGroup[F],
      waitForAll: Boolean
  ): F[TaskProducer[F]] =
    Async[F].pure(
      new TaskProducer[F](queue, supervisor, waitGroup, waitForAll)
    )
}
