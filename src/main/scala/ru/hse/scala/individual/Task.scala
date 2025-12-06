package ru.hse.scala.individual

import cats.effect._
import cats.effect.std.{Console, Queue, Supervisor}
import fs2.io.file.Path

object Task extends IOApp {
//сложно читать  тесты
  val prompt                   = "Enter number:"
  val exitCommand              = "exit"
  private val greeting: String =
    "Write ordinal number of a factorial to compute, exit for Exit program.\nInclude --wait to complete all ongoing calculations before exiting."

  def programResource(waitForAll: Boolean, outPath: Path, console: Console[IO]): Resource[IO, Unit] =
    for {
      supervisorA <- Supervisor[IO](waitForAll)
      supervisorB <- Supervisor[IO](waitForAll)
      queue       <- Resource.eval(Queue.unbounded[IO, ProcessMessage])
      _           <- Resource.eval(supervisorA.supervise(new NumberWriter[IO](outPath).run(queue)))
      _           <- Resource.eval(new TaskProducer[IO](queue, supervisorB)(IO.asyncForIO, console).run)
      // был упрощён функционал, тесты перестали работать
      _ <- Resource.eval {
        queue.offer(ProcessMessage.Shutdown)
      }
    } yield ()

  override def run(args: List[String]): IO[ExitCode] = {
    val waitForAll = args.contains("--wait")
    println(greeting)
    programResource(waitForAll, DEFAULT_PATH, implicitly[Console[IO]]).use(_ => IO.unit).as(ExitCode.Success)
  }

  val DEFAULT_PATH: Path = Path("out.txt") // Camel Case
}
