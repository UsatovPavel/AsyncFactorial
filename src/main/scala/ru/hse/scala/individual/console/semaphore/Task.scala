package ru.hse.scala.individual.console.semaphore

import cats.effect._
import cats.effect.std.{Console, Queue, Supervisor}
import fs2.io.file.Path
import ru.hse.scala.individual.console.{NumberWriter, ProcessMessage}

object Task extends IOApp {

  val prompt                   = "Enter number:"
  val exitCommand              = "exit"
  private val greeting: String =
    "Write ordinal number of a factorial to compute, exit for Exit program.\nInclude --wait to complete all ongoing calculations before exiting."

  def programResource(
      waitForAll: Boolean,
      outPath: Path,
      console: Console[IO],
      maxParallel: Int = TaskProducer.DEFAULT_MAX_PROCESS
  ): Resource[IO, Unit] =
    for {
      supAAlloc <- Resource.eval(Supervisor[IO](waitForAll).allocated)
      supBAlloc <- Resource.eval(Supervisor[IO](waitForAll).allocated)
      supervisorA = supAAlloc._1
      releaseA    = supAAlloc._2
      supervisorB = supBAlloc._1
      releaseB    = supBAlloc._2
      queue       <- Resource.eval(Queue.unbounded[IO, ProcessMessage])
      writerFiber <- Resource.eval(supervisorA.supervise(new NumberWriter[IO](outPath).run(queue)))
      _           <- Resource.eval(
        TaskProducer
          .make[IO](queue, supervisorB, maxParallel)(
            IO.asyncForIO,
            console
          )
          .flatMap(_.run)
      )
      _ <- Resource.eval {
        if (waitForAll) releaseB >> queue.offer(ProcessMessage.Shutdown)
        else queue.offer(ProcessMessage.Shutdown) >> releaseB
      }
      _ <- Resource.eval(writerFiber.join)
      _ <- Resource.eval(releaseA)
    } yield ()

  override def run(args: List[String]): IO[ExitCode] = {
    val waitForAll = args.contains("--wait")
    println(greeting)
    programResource(waitForAll, DEFAULT_PATH, implicitly[Console[IO]]).use(_ => IO.unit).as(ExitCode.Success)
  }

  val DEFAULT_PATH: Path = Path("out.txt")
}
