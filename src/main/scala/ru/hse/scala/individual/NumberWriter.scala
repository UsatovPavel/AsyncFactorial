package ru.hse.scala.individual

import cats.effect._
import cats.effect.std.Queue
import cats.implicits.{catsSyntaxFlatMapOps, toFlatMapOps}
import fs2.Stream
import fs2.io.file.{Files, Flags, Path}

import java.nio.charset.StandardCharsets

class NumberWriter[F[_]:Concurrent : Files] {

  val outputFilepath = Path("out.txt")
  private def process(deferred: Deferred[F, Either[ParseError, BigInt]]): F[Unit] = {
    deferred.get.flatMap {
      case Left(_) => Concurrent[F].unit
      case Right(number: BigInt) => {
        Stream
          .emits(s"$number\n".getBytes(StandardCharsets.UTF_8))
          .covary[F]
          .through(Files[F].writeAll(outputFilepath, Flags.Append))
          .compile
          .drain
      }
    }
  }

  def run(queue: Queue[F, Deferred[F, Either[ParseError, BigInt]]]): F[Unit] ={
        queue.take.flatMap(process).foreverM
    }
}
