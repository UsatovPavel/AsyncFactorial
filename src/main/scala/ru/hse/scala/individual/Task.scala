package ru.hse.scala.individual

import cats.effect._
import fs2.io.file.Path

object Task extends IOApp {
  val prompt                   = "Enter number:"
  val exitCommand              = "exit"
  private val greeting: String = "Write ordinal number of a factorial to compute, exit for Exit program.\n" +
    "Include --wait to complete all ongoing calculations before exiting."
  override def run(args: List[String]): IO[ExitCode] = {
    val waitForAll = args.contains("--wait")
    println(greeting)
    val program: Resource[IO, Unit] = for {
      active <- Resource.eval(Ref.of[IO, Set[Fiber[IO, Throwable, Unit]]](Set.empty))
      queue  <- NumberWriter.runResourse[IO](DEFAULT_PATH, active)
      _      <- TaskProducer.runResource[IO](
        queue = queue,
        active = active,
        waitForAll = waitForAll
      )
    } yield ()

    program
      .use(_ => IO.never)
      .as(ExitCode.Success)
  }
  val DEFAULT_PATH: Path = Path("out.txt")
}
