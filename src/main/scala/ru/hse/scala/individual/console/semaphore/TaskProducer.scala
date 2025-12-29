package ru.hse.scala.individual.console.semaphore

import cats.effect._
import cats.effect.implicits.monadCancelOps_
import cats.effect.std.{Console, Queue, Semaphore, Supervisor}
import cats.implicits._
import ru.hse.scala.individual.console.ProcessMessage
import ru.hse.scala.individual.core.FactorialAccumulator

//можно представить как loop в Task, но вынесен в класс для расширяемости архитектуры
final class TaskProducer[F[_]: Async: Console](
    queue: Queue[F, ProcessMessage],
    workerSupervisor: Supervisor[F],
    sem: Semaphore[F]
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
    for {
      _ <- sem.acquire
      _ <- workerSupervisor.supervise(
        FactorialAccumulator
          .inputNumber(text, queue)
          .guarantee(sem.release)
      )
    } yield ()
}

object TaskProducer {
  val DEFAULT_MAX_PROCESS: Int = 10e5.toInt
  def make[F[_]: Async: Console](
      queue: Queue[F, ProcessMessage],
      workerSupervisor: Supervisor[F],
      maxParallel: Int = DEFAULT_MAX_PROCESS
  ): F[TaskProducer[F]] =
    Semaphore[F](maxParallel.toLong).map(sem =>
      new TaskProducer[F](queue, workerSupervisor, sem)
    )
}
