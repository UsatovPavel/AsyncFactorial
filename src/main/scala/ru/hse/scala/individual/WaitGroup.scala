package ru.hse.scala.individual

import cats.effect.{Async, Deferred, Ref}
import cats.syntax.all._
import cats.effect.Fiber

final class WaitGroup[F[_]] private (
    counter: Ref[F, Int],
    done: Deferred[F, Unit]
)(implicit F: Async[F]) {

  /** Регистрирует уже запущенный fiber: увеличивает счётчик и запускает background-обработчик, который уменьшит счётчик
    * при завершении fiber-а и завершит done, если счетчик стал 0.
    */
  def register(fiber: Fiber[F, Throwable, Unit]): F[Unit] =
    for {
      _ <- counter.update(_ + 1)
      _ <- F.start {
        fiber.join.attempt.flatMap(_ =>
          counter.updateAndGet(_ - 1).flatMap { n =>
            if (n == 0) done.complete(()).void else F.unit
          }
        )
      }.void
    } yield ()

  /** Ждёт, пока все зарегистрированные через register fibers завершатся */
  def await: F[Unit] =
    counter.get.flatMap {
      case 0 => F.unit
      case _ => done.get
    }
}

object WaitGroup {
  def create[F[_]: Async]: F[WaitGroup[F]] =
    for {
      ref  <- Ref.of(0)
      done <- Deferred[F, Unit]
    } yield new WaitGroup[F](ref, done)
}
