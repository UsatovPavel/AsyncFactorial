package ru.hse.scala.individual

import cats.effect._
import cats.effect.std.Queue
import fs2.io.file.{Files, Path}
import weaver.SimpleIOSuite

import scala.concurrent.duration.Duration
import scala.util.Random

object TaskSpec extends SimpleIOSuite {
  val mixedInput: List[String] = {
    val numbers   = List.fill(50)(Random.nextInt(20).toString)
    val incorrect = List.fill(50)("not-a-number")
    Random.shuffle(numbers ++ incorrect)
  }
  def delayFiberCancel(time: Duration, writerFiber: FiberIO[Unit]): IO[Unit] = {
    Temporal[IO].sleep(time) >> writerFiber.cancel
  }
  test("taskProducer prints Exit on exit input") {
    val program = for {
      inputs  <- Ref.of[IO, List[String]](List("exit"))
      outputs <- Ref.of[IO, List[String]](List(Task.prompt))
      console = new TestConsole[IO](inputs, outputs)
      queue <- Queue.unbounded[IO, Either[Unit, Deferred[IO, Either[ParseError, BigInt]]]]
      writer = new NumberWriter[IO](Path("out.txt"))
      writerFiber   <- writer.run(queue).start
      active        <- Ref.of[IO, Set[Fiber[IO, Throwable, Unit]]](Set.empty)
      producerFiber <- Task.taskProducer[IO](queue, active, waitForAll = false)(
        IO.asyncForIO,
        console
      ).start
      _       <- producerFiber.join
      outVec  <- outputs.get
      outcome <- writerFiber.join
      // иначе не запишет всё до вызова get
    } yield (outVec, outcome)

    program.flatMap { case (outVec, outcome) =>
      val printed              = outVec.mkString("|")
      val finishedSuccessfully = outcome match {
        case cats.effect.Outcome.Succeeded(_) => true
        case _                                => false
      }
      IO.pure {
        expect(finishedSuccessfully) and expect(printed.contains("Exit"))
      }
    }
  }
  def runTaskProducerWithFile(
      inputsList: List[String],
      initialOutput: List[String],
      outputFile: Path
  ): IO[ProducerResult] = {
    for {
      inputsRef  <- Ref.of[IO, List[String]](inputsList)
      outputsRef <- Ref.of[IO, List[String]](initialOutput)
      console = new TestConsole[IO](inputsRef, outputsRef)
      queue <- Queue.unbounded[IO, Either[Unit, Deferred[IO, Either[ParseError, BigInt]]]]
      _     <- outputFile.parent match {
        case Some(parent) => Files[IO].createDirectories(parent)
        case None         => IO.unit
      }
      writer = new NumberWriter[IO](outputFile)
      writerFiber <- writer.run(queue).start
      active      <- Ref.of[IO, Set[Fiber[IO, Throwable, Unit]]](Set.empty)
      _           <- Task.taskProducer[IO](queue, active, waitForAll = true)(
        IO.asyncForIO,
        console
      )
      // у нас есть NumberWriter который нельзя join, поэтому Sleep делаем
      _            <- writerFiber.join
      outVec       <- outputsRef.get
      exists       <- Files[IO].exists(outputFile)
      fileContents <- if (exists)
        Files[IO].readAll(outputFile)
          .through(fs2.text.utf8.decode)
          .compile
          .string
      else IO.pure("")
    } yield ProducerResult(outVec, fileContents.split("\n").toList.filter(_.nonEmpty))
  }

  def correctFileContentBigint(list: List[Right[Nothing, BigInt]]): List[String] =
    list.flatMap(elem => FactorialAccumulator.factorial(elem.value.intValue).map(_.toString))

  def expectedFileContentString(input: List[String]): List[String] =
    input.flatMap(s => s.toIntOption)
      .flatMap(n => FactorialAccumulator.factorial(n))
      .map(_.toString)
  def fromNumberWriterInput(list: List[Right[Nothing, BigInt]]): IO[ProducerResult] = {
    val input         = list.map(elem => elem.value.toString()) ++ List(Task.exitCommand)
    val outputConsole = list.map(_ => Task.prompt)
    for {
      tmpPath <- Files[IO].createTempFile(None, "mixed-", ".txt", None)
      results <- runTaskProducerWithFile(input, outputConsole, tmpPath)
      _       <- Files[IO].deleteIfExists(tmpPath)
    } yield results
  }

  final case class ProducerResult(
      console: List[String],
      file: List[String]
  )
  test("taskProducer multiply output") {
    for {
      results <- fromNumberWriterInput(NumberWriterSpec.smallList)
      expected = correctFileContentBigint(NumberWriterSpec.smallList)
    } yield expect(results.file == expected)
    for {
      results <- fromNumberWriterInput(NumberWriterSpec.greatListSmallValues)
      expected = correctFileContentBigint(NumberWriterSpec.greatListSmallValues)
    } yield expect(results.file == expected)
  }
  test("taskProducer mixed output") {
    val input         = mixedInput :+ Task.exitCommand
    val outputConsole = mixedInput.map(_ => Task.prompt)
    for {
      tmpPath <- Files[IO].createTempFile(None, "mixed-", ".txt", None)
      results <- runTaskProducerWithFile(input, outputConsole, tmpPath)
      _       <- Files[IO].deleteIfExists(tmpPath)
      expected = expectedFileContentString(mixedInput)
    } yield expect(results.file == expected)
  }
}
