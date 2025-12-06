package ru.hse.scala.individual

import cats.effect._
import cats.effect.std.{Queue, Supervisor}
import fs2.io.file.{Files, Path}
import fs2.text
import weaver.SimpleIOSuite

import scala.util.Random

object TaskSpec extends SimpleIOSuite {
  val mixedInput: List[String] = {
    val numbers   = List.fill(50)(Random.nextInt(20).toString)
    val incorrect = List.fill(50)("not-a-number")
    Random.shuffle(numbers ++ incorrect)
  }

  test("taskProducer prints Exit on exit input") {
    val program = for {
      inputs  <- Ref.of[IO, List[String]](List("exit"))
      outputs <- Ref.of[IO, List[String]](List(Task.prompt))
      console = new TestConsole[IO](inputs, outputs)
      queue       <- Queue.unbounded[IO, ProcessMessage]
      writerFiber <- new NumberWriter[IO](Path("out.txt")).run(queue).start
      supervisorR <- Supervisor[IO].allocated
      supervisor = supervisorR._1
      releaseSup = supervisorR._2
      producerFiber <- TaskProducer
        .make[IO](queue, supervisor)(IO.asyncForIO, console)
        .flatMap(_.run)
        .start
      _       <- producerFiber.join
      _       <- queue.offer(ProcessMessage.Shutdown)
      outcome <- writerFiber.join
      outVec  <- outputs.get
      _       <- releaseSup
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

  final case class ProducerResult(
      console: List[String],
      file: List[String]
  )

  def runTaskWithProgramResource(
      inputsList: List[String],
      initialOutput: List[String],
      outputFile: Path
  ): IO[ProducerResult] =
    for {
      inputsRef  <- Ref.of[IO, List[String]](inputsList)
      outputsRef <- Ref.of[IO, List[String]](initialOutput)
      console = new TestConsole[IO](inputsRef, outputsRef)
      _           <- Task.programResource(waitForAll = true, outputFile, console).use(_ => IO.unit)
      out         <- outputsRef.get
      exists      <- Files[IO].exists(outputFile)
      fileContent <-
        if (exists)
          Files[IO]
            .readAll(outputFile)
            .through(text.utf8.decode)
            .compile
            .string
            .map(_.split("\n", -1).toList)
        else IO.pure(List.empty)
    } yield ProducerResult(out, fileContent)

  def expectedFileContentString(input: List[String]): List[String] =
    input.map {
      case s if s.toIntOption.isDefined =>
        val n = s.toInt
        val v = FactorialAccumulator.factorial(n)
        v match {
          case Some(value) => s"$n = $value"
          case _           => {
            val err = ParseError.CalculationError(s)
            s"${err.input}: ${err.errorMessage}"
          }
        }
      case _ =>
        "not-a-number parse error: wrong number"
    }

  def fromNumberWriterInput(list: List[Either[ParseError, BigInt]]): IO[ProducerResult] = {
    val input         = list.collect { case Right(v) => v.toString() } ++ List(Task.exitCommand)
    val outputConsole = list.collect { case Right(_) => Task.prompt }
    for {
      tmpPath <- Files[IO].createTempFile(None, "mixed-", ".txt", None)
      results <- runTaskWithProgramResource(input, outputConsole, tmpPath)
      _       <- Files[IO].deleteIfExists(tmpPath)
    } yield results
  }

  test("taskProducer multiply output") {
    for {
      r1 <- fromNumberWriterInput(TestUtils.smallInputs.toEitherBigIntList)
      e1 <- TestUtils.smallInputs.expectedStringsIO()
      smallExpect = expect(TestUtils.checkNumberOutput(r1.file, e1))

      r2 <- fromNumberWriterInput(TestUtils.mediumInputsSmallValues.toEitherBigIntList)
      e2 <- TestUtils.mediumInputsSmallValues.expectedStringsIO()
      greatExpect = expect(TestUtils.checkNumberOutput(r2.file, e2))
    } yield smallExpect.and(greatExpect)
  }

  test("taskProducer mixed output") {
    val input         = mixedInput :+ Task.exitCommand
    val outputConsole = mixedInput.map(_ => Task.prompt)
    for {
      tmpPath <- Files[IO].createTempFile(None, "mixed-", ".txt", None)
      results <- runTaskWithProgramResource(input, outputConsole, tmpPath)
      _       <- Files[IO].deleteIfExists(tmpPath)
      expected = expectedFileContentString(mixedInput)
      // _ <- IO.println(TestUtils.diffOutput(results.file, expected))
    } yield expect(TestUtils.checkNumberOutput(results.file, expected))
  }
  test("taskProducer huge input") {
    for {
      r        <- fromNumberWriterInput(TestUtils.largeInputData.toEitherBigIntList)
      expected <- TestUtils.largeInputData.expectedStringsIO()
    } yield expect(TestUtils.checkNumberOutput(r.file, expected))
  }

  test("Сancel whole program even with huge pending input") {
    val inputStrings = TestUtils.largeInputData.values.map(_.toString)

    val program =
      for {
        inputsRef  <- Ref.of[IO, List[String]](inputStrings)
        outputsRef <- Ref.of[IO, List[String]](List.fill(inputStrings.length)(Task.prompt))
        console = new TestConsole[IO](inputsRef, outputsRef)

        // запускаем как реальную программу — resource сам отменит writer
        fiber <- Task
          .programResource(waitForAll = false, Path("cancel-test.txt"), console)
          .use(_ => IO.never)
          .start
        // без этой строки или обычного sleep могло ничего не запуститься перед cancel и тест бы отработал успешно
        _ <- TestUtils.waitForOutputsAtLeast(outputsRef, 1)

        _       <- fiber.cancel
        outcome <- fiber.join
      } yield outcome

    program.map { outcome =>
      expect(outcome match {
        case cats.effect.Outcome.Canceled() => true
        case _                              => false
      })
    }
  }
}
