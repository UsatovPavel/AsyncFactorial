package ru.hse.scala.individual

import cats.effect._
import cats.effect.std.{Console, Queue, Supervisor}
import fs2.Stream
import fs2.io.file.Path

/** Точка входа: поднимает очередь, два Supervisor'а и запускает Producer + Writer.
  *
  * Важная деталь для waitForAll=true:
  * - supervisorB управляет воркерами вычисления факториала.
  * - После того как Producer получил "exit", вызываем releaseB.
  * - При waitForAll=true release блокируется и ждёт завершения всех supervised задач.
  * - Только после того как все воркеры завершились и положили свои результаты в очередь,
  *   мы отправляем Shutdown в writer, чтобы ничего не потерялось.
  *
  * Альтернативный подход:
  * - Можно использовать 1 Supervisor (без ручного release) + Ref[Int] для учёта активных воркеров,
  *   затем явно ждать пока счётчик станет 0 перед отправкой Shutdown.
  *   Это доп. синхронизация и более громоздко(добавлять Ref[Int] в TaskProducer) т.к. в текущем варианте сам Supervisor
  *   при waitForAll=true уже умеет ждать все задачи, не нужен явный счётчик.
  */
object Task extends IOApp {
  val prompt                   = "Enter number:"
  val exitCommand              = "exit"
  private val greeting: String =
    "Write ordinal number of a factorial to compute, exit for Exit program.\nInclude --wait to complete all ongoing calculations before exiting."

  def programResourceWithStream(
      waitForAll: Boolean,
      outPath: Path,
      input: Stream[IO, String]
  ): Resource[IO, Unit] =
    for {
      (supervisorA, releaseA) <- Resource.eval(Supervisor[IO](waitForAll).allocated)
      (supervisorB, releaseB) <- Resource.eval(Supervisor[IO](waitForAll).allocated)
      queue                   <- Resource.eval(Queue.unbounded[IO, ProcessMessage])
      writerFiber             <- Resource.eval(supervisorA.supervise(new NumberWriter[IO](outPath).run(queue)))
      _                       <- Resource.eval(new TaskProducer[IO](queue, supervisorB).run(input))
      _ <- Resource.eval {
        if (waitForAll) releaseB >> queue.offer(ProcessMessage.Shutdown)
        else queue.offer(ProcessMessage.Shutdown) >> releaseB
      }
      _ <- Resource.eval(writerFiber.join)
      _ <- Resource.eval(releaseA)
    } yield ()

  def programResource(waitForAll: Boolean, outPath: Path, console: Console[IO]): Resource[IO, Unit] = {
    val inputStream =
      Stream.repeatEval(console.println(prompt) *> console.readLine)
    for {
      _ <- programResourceWithStream(waitForAll, outPath, inputStream)
      _ <- Resource.eval(console.println("Exit"))
    } yield ()
  }

  override def run(args: List[String]): IO[ExitCode] = {
    val waitForAll = args.contains("--wait")
    println(greeting)
    programResource(waitForAll, DefaultPath, implicitly[Console[IO]]).use(_ => IO.unit).as(ExitCode.Success)
  }

  val DefaultPath: Path = Path("out.txt")
}
