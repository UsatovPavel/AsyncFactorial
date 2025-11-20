package ru.hse.scala.individual

import cats.effect.std.{Console, Queue}
import cats.effect._
import cats.implicits.{catsSyntaxFlatMapOps, toFlatMapOps, toFunctorOps}
//import cats.syntax.all._ // import for syntax

object Task extends IOApp {
  private def taskProducer[F[_]: Concurrent: Console](
      taskConsumer: Fiber[F, Throwable, Unit],
      queue: Queue[F, Deferred[F, Either[ParseError, BigInt]]]
  ) = {
    def loop: F[Unit] = (Console[F].println("Enter factorial:") >>
      Console[F].readLine.flatMap { text =>
        text.trim match {
          case "exit" => {
            Console[F].println("Exit")
            taskConsumer.cancel
          }
          case _ =>
            for {
              deferred <- Deferred[F, Either[ParseError, BigInt]]
              _        <- queue.offer(deferred)
              _        <- FactorialAccumulator.inputNumber(text, deferred).void
              _        <- loop
            } yield ()
        }
      })
    loop
  }
  override def run(args: List[String]): IO[ExitCode] = {
    // Queue[IO, Deferred[IO, Either[ParseError, BigInt]]]
    for {
      queue <- Queue.unbounded[IO, Deferred[IO, Either[ParseError, BigInt]]]
      fiber <- new NumberWriter[IO]().run(queue).start
      _     <- taskProducer[IO](fiber, queue)
    } yield ExitCode.Success
  }
}
