package ru.hse.scala.individual.console

import cats.effect._
import cats.effect.std.Queue
import cats.implicits._
import fs2.Stream
import fs2.io.file.{Files, Flags, Path}

import java.nio.charset.StandardCharsets

/** Считывает результаты из очереди и один раз открытым потоком дописывает их в файл. Все записи идут через один
  * открытый файловый поток.
  */
final class NumberWriter[F[_]: Async: Files](path: Path) {
  private def renderedBytes(rendered: Rendered): Stream[F, Byte] =
    rendered match {
      case Rendered.Line(line) =>
        Stream
          .emits((line + "\n").getBytes(StandardCharsets.UTF_8))
          .covary[F]
      case Rendered.Stop => Stream.empty
    }

  private def messagesToBytes(queue: Queue[F, ProcessMessage]): Stream[F, Byte] =
    Stream
      .repeatEval(queue.take)
      .takeWhile {
        case ProcessMessage.Shutdown => false
        case _                       => true
      }
      .flatMap(msg => renderedBytes(msg.render))

  def run(queue: Queue[F, ProcessMessage]): F[Unit] =
    messagesToBytes(queue)
      .through(Files[F].writeAll(path, Flags.Append))
      .compile
      .drain
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

    Resource.make(acquire)(release).map { case (q, _) => q }
  }
}
