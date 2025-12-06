package ru.hse.scala.individual

import cats.effect.IO
import fs2.Stream

final case class IntInputs(values: List[Int]) {
  def toEitherBigIntList: List[Either[ParseError, BigInt]] = {
    values.map(n => Right(BigInt(n)))
  }

  def ioResults(parallelism: Int = Runtime.getRuntime.availableProcessors()): IO[List[FactorialResult]] =
    Stream
      .emits(values)
      .covary[IO]
      .parEvalMap(parallelism)(n =>
        IO(FactorialAccumulator.factorial(n)).map(_.map(v => FactorialResult(n, v)))
      )
      .compile
      .toList
      .map(_.flatten)
  def expectedStringsIO(parallelism: Int = Runtime.getRuntime.availableProcessors()): IO[List[String]] =
    ioResults(parallelism).map { results =>
      results.map(r => ProcessMessage.Completed(r).line)
    }
}
object IntInputs {
  def expectedStrings(results: List[FactorialResult]): List[String] =
    results.map(r => s"${r.input} = ${r.value}")

}
