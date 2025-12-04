package ru.hse.scala.individual

import cats.effect._
import cats.effect.std.Supervisor
import fs2.io.file.Path

object Task extends IOApp {

  val prompt                   = "Enter number:"
  val exitCommand              = "exit"
  private val greeting: String =
    "Write ordinal number of a factorial to compute, exit for Exit program.\n" +
      "Include --wait to complete all ongoing calculations before exiting."

  override def run(args: List[String]): IO[ExitCode] = {
    val waitForAll = args.contains("--wait")
    print(greeting)
    val program: Resource[IO, Unit] = for {
      supervisor <- Supervisor[IO]                             // Supervisor как Resource
      queue      <- NumberWriter.runResource[IO](DEFAULT_PATH) // очередь + writer fiber
      wg         <- Resource.eval(WaitGroup.create[IO])        // wait-group для --wait
      producer   <- Resource.eval(TaskProducer.make[IO](queue, supervisor, wg, waitForAll))
      _          <- Resource.make {
        Concurrent[IO].start(producer.run)
      } { fib =>
        fib.cancel
      }
    } yield ()

    program.use(_ => IO.never).as(ExitCode.Success)
  }
  val DEFAULT_PATH: Path = Path("out.txt")
}
/*
              +-------------------------+
              |        Main (IOApp)     |
              +-------------------------+
                         |
                         V
               create Supervisor
                         |
            +------------+-------------+
            |            |             |
            V            V             V
  TaskReader (основной поток)      Supervisor
            |                      (дерево задач)
            |                     /        |       \
            |                    /         |        \
            |                   V          V         V
            |               NumberWriter  Worker1   Worker2 ...
            |                     (в фоне)
            |
        вызывает
   supervisor.supervise(...)
   чтобы запускать Worker

 */
