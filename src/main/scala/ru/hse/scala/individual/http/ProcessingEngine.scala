package ru.hse.scala.individual.http

import cats.effect.Async
import cats.syntax.all._
import fs2.Stream
import ru.hse.scala.individual.core.FactorialAccumulator
import ru.hse.scala.individual.core.ParseError

/** Core processing logic that is transport-agnostic (HTTP, Kafka).
  *
  * It computes factorials concurrently and returns results in arbitrary order.
  */
final class ProcessingEngine[F[_]: Async](
    parallelism: Int,
) {
  def run(items: List[TaskItem]): F[List[ResultItem]] =
    Stream
      .emits(items)
      .covary[F]
      .parEvalMapUnordered(parallelism)(computeOne)
      .compile
      .toList

  private def computeOne(item: TaskItem): F[ResultItem] =
    if (item.input < 0) {
      Async[F].pure(
        ResultItem.Failure(item.jobId, item.itemId, item.input, ParseError.ErrorMessage.NegativeNumber)
      )
    } else {
      Async[F]
        .blocking(FactorialAccumulator.factorial(item.input))
        .map {
          case Some(value) =>
            ResultItem.Success(item.jobId, item.itemId, item.input, value.toString)
          case None =>
            ResultItem.Failure(item.jobId, item.itemId, item.input, ParseError.ErrorMessage.Calculation)
        }
    }
}

object ProcessingEngine {
  def make[F[_]: Async](parallelism: Int): F[ProcessingEngine[F]] =
    if (parallelism > 0) Async[F].pure(new ProcessingEngine[F](parallelism))
    else Async[F].raiseError(new IllegalArgumentException("parallelism must be > 0"))
}
