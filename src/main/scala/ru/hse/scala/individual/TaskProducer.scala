package ru.hse.scala.individual

import cats.effect._
import cats.effect.implicits.genSpawnOps
import cats.effect.std.{Console, Queue}
import cats.implicits._

final class TaskProducer[F[_]: Concurrent: Console](
    queue: Queue[F, ProcessMessage[F]],
    activeRef: Ref[F, Set[Fiber[F, Throwable, Unit]]],
    waitForAll: Boolean
) {

  private val prompt      = Task.prompt
  private val exitCommand = Task.exitCommand

  def run: F[Unit] = loop

  private def loop: F[Unit] =
    Console[F].println(prompt) *>
      Console[F].readLine.flatMap { text =>
        text.trim match {
          case t if t == exitCommand =>
            handleExit
          case other =>
            spawnWorker(other) >> loop
        }
      }

  private def spawnWorker(text: String): F[Unit] =
    for {
      deferred <- Deferred[F, Either[ParseError, BigInt]]
      _        <- queue.offer(ProcessMessage.DeferredMsg(deferred))
      fib      <- Concurrent[F].start(FactorialAccumulator.inputNumber(text, deferred).void)
      _        <- activeRef.update(_ + fib)
      _        <- fib.join.attempt.flatMap(_ => activeRef.update(_ - fib)).start
    } yield ()

  private def handleExit: F[Unit] =
    for {
      set <- activeRef.get
      _   <- if (waitForAll) set.toList.traverse_(_.join) else set.toList.traverse_(_.cancel)
      _   <- queue.offer(ProcessMessage.Shutdown[F]())
      _   <- Console[F].println("Exit")
    } yield ()
}

object TaskProducer {
  def runResource[F[_]: Concurrent: Console](
      queue: Queue[F, ProcessMessage[F]],
      active: Ref[F, Set[Fiber[F, Throwable, Unit]]],
      waitForAll: Boolean
  ): Resource[F, Fiber[F, Throwable, Unit]] =
    Resource.make {
      new TaskProducer(queue, active, waitForAll).run.start
    } { fiber =>
      fiber.cancel
    }
}
