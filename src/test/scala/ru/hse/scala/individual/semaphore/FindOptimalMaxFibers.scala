package ru.hse.scala.individual.semaphore

import cats.effect._
import cats.effect.std.{Queue, Supervisor}
import cats.implicits.toTraverseOps
import fs2.io.file.Files
import ru.hse.scala.individual.{NumberWriter, ProcessMessage}

import scala.concurrent.duration._
import scala.util.Random

object FindOptimalMaxFibers extends IOApp.Simple {

  val count: Int                                          = 30000
  val runs: List[Int]                                     = List(100, 1000, 10000)
  val nums: List[Int]                                     = List.fill(count)(Random.nextInt(30))
  def oneRun(maxParallel: Int): IO[(Int, FiniteDuration)] = {
    val inputs  = nums.map(_.toString) :+ Task.exitCommand
    val prompts = List.fill(inputs.length)(Task.prompt)

    for {
      tmpPath <- Files[IO].createTempFile(
        None,
        s"fibers-$maxParallel-",
        ".txt",
        None
      )

      inputsRef  <- Ref.of[IO, List[String]](inputs)
      outputsRef <- Ref.of[IO, List[String]](prompts)
      console    <- TestConsole.fromRef(inputsRef, outputsRef)

      queue <- Queue.unbounded[IO, ProcessMessage]

      supRes <- Supervisor[IO](await = true).allocated
      sup     = supRes._1
      release = supRes._2

      writerFiber <- new NumberWriter[IO](tmpPath).run(queue).start

      start <- IO.monotonic

      producerFiber <- TaskProducer
        .make[IO](queue, sup, maxParallel)(IO.asyncForIO, console)
        .flatMap(_.run)
        .start

      // продьюсер прочитал все входы
      _ <- producerFiber.join

      // ждём, пока Supervisor дождётся всех workers
      _ <- release

      // останавливаем писателя
      _ <- queue.offer(ProcessMessage.Shutdown)
      _ <- writerFiber.join

      end <- IO.monotonic

      // проверяем выходной файл
      exists <- Files[IO].exists(tmpPath)
      lines  <- if (exists)
        Files[IO]
          .readAll(tmpPath)
          .through(fs2.text.utf8.decode)
          .through(fs2.text.lines)
          .compile
          .toList
      else IO.pure(List.empty)

      nonEmpty = lines.count(_.nonEmpty)

      _ <- Files[IO].deleteIfExists(tmpPath)

      // sanity-check – убедиться, что все строки записались
      _ <- IO.raiseUnless(nonEmpty == nums.size)(
        new RuntimeException(
          s"Expected ${nums.size} lines but got $nonEmpty (maxParallel=$maxParallel)"
        )
      )

      duration = end - start

    } yield (maxParallel, duration)
  }

  override def run: IO[Unit] =
    for {
      _ <- IO.println(s"Running performance tests on $count inputs...\n")

      results <- runs.traverse(oneRun)

      _ <- IO.println("\n=== RESULTS ===")
      _ <- results.traverse { case (mp, d) =>
        IO.println(f"maxParallel=$mp%6d  time=${d.toMillis}%,8d ms")
      }

      _ <- IO.println("================\nDone.")
    } yield ()
}
