package ru.hse.scala.individual

import cats.effect._
import cats.effect.std.{Console, Queue, Supervisor}
import fs2.io.file.Path

/** Точка входа: поднимает очередь, единственный Supervisor и запускает Producer + Writer. */
object Task extends IOApp {
//сложно читать  тесты
  val prompt                   = "Enter number:"
  val exitCommand              = "exit"
  private val greeting: String =
    "Write ordinal number of a factorial to compute, exit for Exit program.\nInclude --wait to complete all ongoing calculations before exiting."

  def programResource(waitForAll: Boolean, outPath: Path, console: Console[IO]): Resource[IO, Unit] =
    for {
      supervisor  <- Supervisor[IO](waitForAll)
       queue       <- Resource.eval(Queue.unbounded[IO, ProcessMessage])
       activeCount <- Resource.eval(Ref.of[IO, Int](0))
      writerFiber <- Resource.eval(supervisor.supervise(new NumberWriter[IO](outPath).run(queue)))
       _           <- Resource.eval(new TaskProducer[IO](queue, supervisor, activeCount)(IO.asyncForIO, console).run)
       _ <- Resource.eval {
         def waitActive: IO[Unit] =
           activeCount.get.flatMap {
             case 0 => IO.unit
             case _ => IO.sleep(10.millis) >> waitActive
           }
         IO.whenA(waitForAll)(waitActive)
       }
       _           <- Resource.eval(queue.offer(ProcessMessage.Shutdown))
       _           <- Resource.eval(writerFiber.join) // дождаться записи перед завершением Resource
    } yield ()

  override def run(args: List[String]): IO[ExitCode] = {
    val waitForAll = args.contains("--wait")
    println(greeting)
    programResource(waitForAll, DefaultPath, implicitly[Console[IO]]).use(_ => IO.unit).as(ExitCode.Success)
  }

  val DefaultPath: Path = Path("out.txt")
}
