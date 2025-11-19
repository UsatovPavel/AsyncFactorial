package ru.hse.scala.individual

import cats.effect.std.Console
import cats.effect.{ExitCode, IO, IOApp, Sync}
import cats.implicits.catsStdBimonadForFunction0.*>
import cats.implicits.{catsSyntaxFlatMapOps, toFlatMapOps}
import cats.effect.std.Queue
import cats.instances.queue

import scala.concurrent.Promise
//import cats.syntax.all._ // import for syntax

object Task extends IOApp {
  private def taskProducer[F[_]: Sync: Console]: F[Nothing] = {
    val queue = Queue.unbounded[F, Promise[Either[ParseError, BigInt]]]
    (Console[F].println("Enter factorial:") >>
      Console[F].readLine.flatMap { text =>
        val promise = Promise[Either[ParseError, BigInt]]()
        queue.offer(promise)
        FactorialAccumulator.processInput(text, promise) // кладём в очередь и ждём следующее значение
      }).foreverM
  }
  override def run(args: List[String]): IO[ExitCode] =
    taskProducer[IO].as(ExitCode.Success)
}
