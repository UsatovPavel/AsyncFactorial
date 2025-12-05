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
      producerFiber <- new TaskProducer[IO](queue, supervisor)(IO.asyncForIO, console).run.start
      _             <- producerFiber.join
      _             <- queue.offer(ProcessMessage.Shutdown)
      outcome       <- writerFiber.join
      outVec        <- outputs.get
      _             <- releaseSup
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

  def correctFileContentBigint(list: List[Right[Nothing, BigInt]]): List[String] =
    list.flatMap(elem =>
      FactorialAccumulator
        .factorial(elem.value.intValue)
        .map(v => s"${elem.value} = $v")
    )

  def expectedFileContentString(input: List[String]): List[String] =
    input.map {
      case s if s.toIntOption.isDefined =>
        val n = s.toInt
        val v = FactorialAccumulator.factorial(n).get
        s"$n = $v"
      case _ =>
        "not-a-number parse error: wrong number"
    }

  def fromNumberWriterInput(list: List[Right[Nothing, BigInt]]): IO[ProducerResult] = {
    val input         = list.map(elem => elem.value.toString()) ++ List(Task.exitCommand)
    val outputConsole = list.map(_ => Task.prompt)
    for {
      tmpPath <- Files[IO].createTempFile(None, "mixed-", ".txt", None)
      results <- runTaskWithProgramResource(input, outputConsole, tmpPath)
      _       <- Files[IO].deleteIfExists(tmpPath)
    } yield results
  }

  def some(): IO[(ProducerResult, List[String], ProducerResult, List[String])] =
    for {
      r1 <- fromNumberWriterInput(TestUtils.smallList)
      e1 = correctFileContentBigint(TestUtils.smallList)
      r2 <- fromNumberWriterInput(TestUtils.mediumListSmallValues)
      e2 = correctFileContentBigint(TestUtils.mediumListSmallValues)
    } yield (r1, e1, r2, e2)

//  test("some outputs everything") {
//    for {
//      data <- some()
//      _    <- IO.println("=== RESULT 1 ===")
//      _    <- IO.println("file:\n" + data._1.file.mkString("\n"))
//      _    <- IO.println("expected file 1:\n" + data._2.mkString("\n"))
//      _    <- IO.println(TestUtils.diffOutput(data._1.file, data._2))
////      _    <- IO.println("\n=== RESULT 2 ===")
////      _    <- IO.println("file:\n" + data._3.file.mkString("\n"))
////      _    <- IO.println("expected file 2:\n" + data._4.mkString("\n"))
//      _ <- IO.println(TestUtils.checkNumberOutput(data._1.file, data._2))
//    } yield expect(true)
//  }

  test("taskProducer multiply output") {
    for {
      r1 <- fromNumberWriterInput(TestUtils.smallList)
      e1          = correctFileContentBigint(TestUtils.smallList)
      smallExpect = expect(TestUtils.checkNumberOutput(r1.file, e1))
//      _           = clue(s"SMALL — file ${r1.file}")
//      _           = clue(s"SMALL — expected$e1")
      r2 <- fromNumberWriterInput(TestUtils.mediumListSmallValues)
      e2          = correctFileContentBigint(TestUtils.mediumListSmallValues)
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
      r <- fromNumberWriterInput(TestUtils.bigListSmallValues)
      expected = correctFileContentBigint(TestUtils.bigListSmallValues)
    } yield expect(TestUtils.checkNumberOutput(r.file, expected))
  }
}
