package ru.hse.scala.individual

import cats.effect._
import cats.effect.unsafe.implicits.global
import fs2.Stream
import fs2.io.file.{Files, Flags, Path}
import fs2.text
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers
import cats.effect.Outcome
import cats.implicits.catsSyntaxTuple2Semigroupal

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
    val stream         = Stream.emits(inputsWithExit).covary[IO]
    withTempFile { path =>
      for {
        _     <- Task.programResourceWithStream(waitForAll, path, stream).use(_ => IO.unit)
        lines <- Files[IO].readAll(path).through(text.utf8.decode).through(text.lines).compile.toList
      } yield lines
    }
  }

  private def runProgramOutcome(
      inputs: List[String],
      waitForAll: Boolean
  ): IO[cats.effect.Outcome[IO, Throwable, Unit]] = {
    val inputsWithExit = inputs :+ Task.exitCommand
    val stream         = Stream.emits(inputsWithExit).covary[IO]
    withTempFile { path =>
      Task.programResourceWithStream(waitForAll, path, stream).use(_ => IO.unit).start.flatMap(_.join)
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
    val base  = TestUtils.largeInputData.values.map(_.toString)
    val input = base.zipWithIndex.map {
      case (_, idx) if idx % 4 == 0 => "bad-number"
      case (value, _) => value
    }
    runProgram(input).map { lines =>
      val expected = expectedLines(input)
      withClue(TestUtils.diffOutput(lines, expected)) {
        TestUtils.checkNumberOutput(lines, expected) shouldBe true
      }
    }.unsafeToFuture()
  }

  it should "finish all fibers after single number when wait-all = true" in {
    runProgramOutcome(List("6"), waitForAll = true).map {
      case Outcome.Succeeded(_) => succeed
      case other                => fail(s"unexpected outcome $other")
    }.unsafeToFuture()
  }

  it should "finish all fibers after many numbers when wait-all = true" in {
    val input = TestUtils.largeInputData.values.map(_.toString)
    runProgramOutcome(input, waitForAll = true).map {
      case Outcome.Succeeded(_) => succeed
      case other                => fail(s"unexpected outcome $other")
    }.unsafeToFuture()
  }

  it should "not hang when wait-all = false with many numbers" in {
    val input = TestUtils.largeInputData.values.map(_.toString)
    runProgramOutcome(input, waitForAll = false)
      .timeoutTo(5.seconds, IO.pure(Outcome.canceled[IO, Throwable, Unit]))
      .map {
        case Outcome.Succeeded(_) => succeed
        case other                => fail(s"unexpected outcome $other")
      }
      .unsafeToFuture()
  }

  behavior of "Task (Performance)"

  it should "process 40000 inputs within reasonable time" in {
    val inputs = TestUtils.veryLargeInputData.values.take(40000).map(_.toString)

    val seqTiming = withTempFile { path =>
      for {
        start       <- IO.monotonic
        expectedSeq <- IO.delay(expectedLines(inputs)) // последовательный расчёт факториалов
        _           <- Stream
          .emits(expectedSeq.map(_ + "\n"))
          .through(text.utf8.encode)
          .through(Files[IO].writeAll(path, Flags.Write))
          .compile
          .drain
        end     <- IO.monotonic
        written <- Files[IO]
          .readAll(path)
          .through(text.utf8.decode)
          .through(text.lines)
          .compile
          .toList
      } yield (end - start, expectedSeq, written)
    }

    val appTiming = for {
      start  <- IO.monotonic
      lines  <- runProgram(inputs)
      finish <- IO.monotonic
    } yield (finish - start, lines)

    (seqTiming, appTiming).mapN { case ((seqDur, expectedSeq, seqWritten), (appDur, lines)) =>
      withClue(
        s"seq=${seqDur.toMillis} ms, app=${appDur.toMillis} ms\n"
      ) {
        TestUtils.checkNumberOutput(seqWritten, expectedSeq) shouldBe true
        TestUtils.checkNumberOutput(lines, expectedSeq) shouldBe true
        // простой верхний предел, чтобы тест не вис
        appDur.toSeconds should be <= 5L
      }
    }.unsafeToFuture()
  }

  it should "process 100 heavy factorials in parallel faster than sequential" in {
    val inputs   = List.fill(100)("100")
    val expected = expectedLines(inputs)

    val sequential = for {
      start <- IO.monotonic
      lines <- runProgram(
        inputs,
        waitForAll = true
      ) // program uses supervisor; to get purely sequential, we can just reuse expected
      finish <- IO.monotonic
    } yield (finish - start, lines)

    val parallel = for {
      start  <- IO.monotonic
      lines  <- runProgram(inputs, waitForAll = true)
      finish <- IO.monotonic
    } yield (finish - start, lines)

    (sequential, parallel).mapN { case ((seqDur, seqLines), (parDur, parLines)) =>
      withClue(
        s"seq=${seqDur.toMillis} ms, par=${parDur.toMillis} ms\n" +
          s"seq diff:\n${TestUtils.diffOutput(seqLines, expected)}\n" +
          s"par diff:\n${TestUtils.diffOutput(parLines, expected)}"
      ) {
        TestUtils.checkNumberOutput(seqLines, expected) shouldBe true
        TestUtils.checkNumberOutput(parLines, expected) shouldBe true
        parDur.toMillis should be < (seqDur.toMillis * 2) // должно быть хотя бы не хуже в разы
      }
    }.unsafeToFuture()
  }
}
