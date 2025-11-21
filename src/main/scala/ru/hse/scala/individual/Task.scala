package ru.hse.scala.individual

import cats.effect.std.{Console, Queue}
import cats.effect._
import cats.implicits.{catsSyntaxFlatMapOps, toFlatMapOps, toFunctorOps}
import fs2.io.file.Path

object Task extends IOApp {
  val prompt      = "Enter factorial:"
  val exitCommand = "exit"
  def taskProducer[F[_]: Concurrent: Console](
      queue: Queue[F, Deferred[F, Either[ParseError, BigInt]]],
      onExit: F[Unit] // for tests
  ) = {
    def loop: F[Unit] = (Console[F].println(prompt) >>
      Console[F].readLine.flatMap { text =>
        text.trim match {
          case t if (t == exitCommand) => {
            Console[F].println("Exit") >> onExit
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
    for {
      queue <- Queue.unbounded[IO, Deferred[IO, Either[ParseError, BigInt]]]
      fiber <- new NumberWriter[IO](DEFAULT_PATH).run(queue).start
      _     <- taskProducer[IO](queue, fiber.cancel)
    } yield ExitCode.Success
  }
  val DEFAULT_PATH = Path("out.txt")
}
