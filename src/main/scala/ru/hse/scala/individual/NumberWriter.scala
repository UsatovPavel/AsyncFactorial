package ru.hse.scala.individual

import cats.effect._
import cats.effect.implicits.genSpawnOps
import cats.effect.std.Queue
import cats.implicits.{catsSyntaxFlatMapOps, toFlatMapOps, toFoldableOps}
import fs2.Stream
import fs2.io.file.{Files, Flags, Path}
import cats.syntax.functor._

import java.nio.charset.StandardCharsets

class NumberWriter[F[_]: Concurrent: Files](val outputFilepath: Path) {
  // Queue на выход NumberWriter
  private def process(item: ProcessMessage[F]): F[Unit] =
    item match {
      case ProcessMessage.Shutdown()            => Concurrent[F].unit
      case ProcessMessage.DeferredMsg(deferred) =>
        deferred.get.flatMap {
          case Left(_)       => Concurrent[F].unit
          case Right(number) =>
            Stream
              .emits(s"$number\n".getBytes(StandardCharsets.UTF_8))
              .covary[F]
              .through(Files[F].writeAll(outputFilepath, Flags.Append))
              .compile
              .drain
        }
    }

  def run(queue: Queue[F, ProcessMessage[F]]): F[Unit] =
    queue.take.flatMap {
      case ProcessMessage.Shutdown()            => Concurrent[F].unit
      case ProcessMessage.DeferredMsg(deferred) => process(ProcessMessage.DeferredMsg(deferred)) >> run(queue)
    }
}
object NumberWriter {
  def runResourse[F[_]: Async: Files](
      path: Path,
      activeRef: Ref[F, Set[Fiber[F, Throwable, Unit]]]
  ): Resource[F, Queue[F, ProcessMessage[F]]] = {

    val acquire: F[(Queue[F, ProcessMessage[F]], Fiber[F, Throwable, Unit])] = for {
      q     <- Queue.unbounded[F, ProcessMessage[F]]
      fiber <- (new NumberWriter[F](path).run(q).start)
    } yield (q, fiber)

    def release(tuple: (Queue[F, ProcessMessage[F]], Fiber[F, Throwable, Unit])): F[Unit] = {
      val (_, writerFiber) = tuple
      for {
        set <- activeRef.get
        _   <- set.toList.traverse_(_.cancel) // отменяем все воркеры
        _   <- writerFiber.cancel             // отменяем writer
      } yield ()
    }

    Resource.make(acquire)(release).map(_._1)
  }
}
