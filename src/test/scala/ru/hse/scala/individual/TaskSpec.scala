package ru.hse.scala.individual

import cats.effect._
import cats.effect.unsafe.implicits.global
import fs2.io.file.Files
import fs2.io.file.Path
import fs2.text
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers
import cats.effect.Outcome
import scala.concurrent.duration._

class TaskSpec extends AsyncFlatSpec with Matchers {

  private def withTempFile[A](use: Path => IO[A]): IO[A] =
    Resource
      .make(Files[IO].createTempFile(None, "task-", ".txt", None))(p => Files[IO].deleteIfExists(p).void)
      .use(use)

  private def expectedLines(inputs: List[String]): List[String] =
    inputs.filterNot(_ == Task.exitCommand).map { raw =>
      raw.trim.toIntOption match {
        case Some(n) if n < 0 =>
          s"$raw ${ParseError.ErrorMessage.NegativeNumber}"
        case Some(n) =>
          FactorialAccumulator
            .factorial(n)
            .map(v => s"$n = $v")
            .getOrElse(s"$raw ${ParseError.ErrorMessage.Calculation}")
        case None =>
          s"$raw ${ParseError.ErrorMessage.WrongNumber}"
      }
    }

  private def runProgram(
      inputs: List[String],
      waitForAll: Boolean = true
  ): IO[List[String]] = {
    val inputsWithExit = inputs :+ Task.exitCommand
    withTempFile { path =>
      for {
        inputsRef  <- Ref.of[IO, List[String]](inputsWithExit)
        outputsRef <- Ref.of[IO, List[String]](List.empty)
        console = new TestConsole[IO](inputsRef, outputsRef)
        _      <- Task.programResource(waitForAll, path, console).use(_ => IO.unit)
        lines  <- Files[IO].readAll(path).through(text.utf8.decode).through(text.lines).compile.toList
      } yield lines
    }
  }

  private def runProgramOutcome(
      inputs: List[String],
      waitForAll: Boolean
  ): IO[cats.effect.Outcome[IO, Throwable, Unit]] = {
    val inputsWithExit = inputs :+ Task.exitCommand
    withTempFile { path =>
      for {
        inputsRef  <- Ref.of[IO, List[String]](inputsWithExit)
        outputsRef <- Ref.of[IO, List[String]](List.empty)
        console = new TestConsole[IO](inputsRef, outputsRef)
        fiber <- Task.programResource(waitForAll, path, console).use(_ => IO.unit).start
        outcome <- fiber.join
      } yield outcome
    }
  }

  behavior of "Task (Group1)"

  it should "write single number" in {
    runProgram(List("5")).map { lines =>
      val expected = expectedLines(List("5"))
      withClue(TestUtils.diffOutput(lines, expected)) {
        TestUtils.checkNumberOutput(lines, expected) shouldBe true
      }
    }.unsafeToFuture()
  }

  it should "write single parse error" in {
    runProgram(List("oops")).map { lines =>
      val expected = expectedLines(List("oops"))
      withClue(TestUtils.diffOutput(lines, expected)) {
        TestUtils.checkNumberOutput(lines, expected) shouldBe true
      }
    }.unsafeToFuture()
  }

  behavior of "Task (Group2)"

  it should "write many numbers" in {
    val input = TestUtils.largeInputData.values.map(_.toString)
    runProgram(input).map { lines =>
      val expected = expectedLines(input)
      withClue(TestUtils.diffOutput(lines, expected)) {
        TestUtils.checkNumberOutput(lines, expected) shouldBe true
      }
    }.unsafeToFuture()
  }

  it should "write many numbers with errors" in {
    val base = TestUtils.largeInputData.values.map(_.toString)
    val input = base.zipWithIndex.map {
      case (_, idx) if idx % 4 == 0 => "bad-number"
      case (value, _)               => value
    }
    runProgram(input).map { lines =>
      val expected = expectedLines(input)
      withClue(TestUtils.diffOutput(lines, expected)) {
        TestUtils.checkNumberOutput(lines, expected) shouldBe true
      }
    }.unsafeToFuture()
  }

  it should "finish all fibers after single number when wait-all = true" in {
    runProgramOutcome(List("6"), waitForAll = true).map { outcome =>
      outcome match {
        case Outcome.Succeeded(_) => succeed
        case other                => fail(s"unexpected outcome $other")
      }
    }.unsafeToFuture()
  }

  it should "finish all fibers after many numbers when wait-all = true" in {
    val input = TestUtils.largeInputData.values.map(_.toString)
    runProgramOutcome(input, waitForAll = true).map { outcome =>
      outcome match {
        case Outcome.Succeeded(_) => succeed
        case other                => fail(s"unexpected outcome $other")
      }
    }.unsafeToFuture()
  }

  it should "not hang when wait-all = false with many numbers" in {
    val input = TestUtils.largeInputData.values.map(_.toString)
    runProgramOutcome(input, waitForAll = false)
      .timeoutTo(5.seconds, IO.pure(Outcome.canceled[IO, Throwable, Unit]))
      .map { outcome =>
        outcome match {
          case Outcome.Succeeded(_) => succeed
          case other                => fail(s"unexpected outcome $other")
        }
      }
      .unsafeToFuture()
  }
}
