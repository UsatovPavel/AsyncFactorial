package ru.hse.scala.individual

import cats.effect.std.{Console, Queue}
import cats.effect._
import cats.effect.implicits.genSpawnOps
import cats.implicits.{catsSyntaxApplicativeError, catsSyntaxFlatMapOps, toFlatMapOps, toFoldableOps, toFunctorOps}
import fs2.io.file.Path

object Task extends IOApp {
  val prompt                   = "Enter number:"
  val exitCommand              = "exit"
  private val greeting: String = "Write ordinal number of a factorial to compute, exit for Exit program.\n" +
    "Include --wait to complete all ongoing calculations before exiting."
  def taskProducer[F[_]: Concurrent: Console](
      queue: Queue[F, Either[Unit, Deferred[F, Either[ParseError, BigInt]]]],
      activeRef: Ref[F, Set[Fiber[F, Throwable, Unit]]],
      waitForAll: Boolean
  ): F[Unit] = {
    def loop: F[Unit] = (Console[F].println(prompt) >>
      Console[F].readLine.flatMap { text =>
        text.trim match {
          case t if (t == exitCommand) => handleExit(queue)
          case _                       => spawnWorker(text) >> loop
        }
      })
    def spawnWorker(text: String): F[Unit] = for {
      deferred <- Deferred[F, Either[ParseError, BigInt]]
      _        <- queue.offer(Right(deferred))
      fib      <- Concurrent[F].start(FactorialAccumulator.inputNumber(text, deferred).void)
      _        <- activeRef.update(_ + fib)
      _        <- fib.join.attempt.flatMap(_ => activeRef.update(_ - fib)).start
    } yield ()
    def handleExit(
        queue: Queue[F, Either[Unit, Deferred[F, Either[ParseError, BigInt]]]]
    ): F[Unit] = for {
      set <- activeRef.get
      _   <- if (waitForAll) set.toList.traverse_(_.join) else set.toList.traverse_(_.cancel)
      _   <- queue.offer(Left(()))
      _   <- Console[F].println("Exit")
    } yield ()
    loop
  }
  override def run(args: List[String]): IO[ExitCode] = {
    println(greeting)
    val waitForAll: Boolean = args.contains("--wait")
    for {
      queue  <- Queue.unbounded[IO, Either[Unit, Deferred[IO, Either[ParseError, BigInt]]]]
      active <- Ref.of[IO, Set[Fiber[IO, Throwable, Unit]]](Set.empty)
      _      <- taskProducer[IO](queue, active, waitForAll)
    } yield ExitCode.Success
  }
  val DEFAULT_PATH: Path = Path("out.txt")
}
