package ru.hse.scala.individual

import cats.effect._
import cats.effect.std.Queue
import fs2.Stream
import fs2.io.file.{Files, Flags, Path}
import cats.implicits._
import java.nio.charset.StandardCharsets

final class NumberWriter[F[_]: Async: Files](path: Path) {

  private def format(msg: ProcessMessage): Option[String] = msg match {
    case ProcessMessage.Completed(res) =>
      Some(s"${res.input} = ${res.value}")
    case ProcessMessage.ParseFailed(err) =>
      Some(s"${err.input} ${err.errorMessage}")
    case ProcessMessage.Shutdown =>
      None
  }

  private def writeLine(line: String): F[Unit] =
    Stream
      .emits((line + "\n").getBytes(StandardCharsets.UTF_8))
      .covary[F]
      .through(Files[F].writeAll(path, Flags.Append))
      .compile
      .drain

  def run(queue: Queue[F, ProcessMessage]): F[Unit] = {
    def loop: F[Unit] =
      queue.take.flatMap { msg =>
        format(msg) match {
          case Some(line) => writeLine(line) >> loop
          case None       => Concurrent[F].unit
        }
      }
    loop
  }
}

object NumberWriter {
  def runResource[F[_]: Async: Files](path: Path): Resource[F, Queue[F, ProcessMessage]] = {
    val acquire: F[(Queue[F, ProcessMessage], Fiber[F, Throwable, Unit])] = for {
      q     <- Queue.unbounded[F, ProcessMessage]
      fiber <- Concurrent[F].start(new NumberWriter[F](path).run(q))
    } yield (q, fiber)

    def release(state: (Queue[F, ProcessMessage], Fiber[F, Throwable, Unit])): F[Unit] = {
      val (q, fiber)  = state
      val tryShutdown = q.offer(ProcessMessage.Shutdown).attempt.void
      for {
        _ <- tryShutdown
        _ <- fiber.cancel
      } yield ()
    }

    Resource.make(acquire)(release).map(_._1)
  }
}
