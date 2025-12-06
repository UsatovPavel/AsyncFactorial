package ru.hse.scala.individual

import cats.effect.kernel.Ref
import cats.effect.std.Queue
import cats.effect.{IO, Resource}
import cats.implicits._
import fs2.io.file.{Files, Path}
import weaver.SimpleIOSuite

import java.util.UUID
import scala.concurrent.duration._

object NumberWriterSpec extends SimpleIOSuite {
  val defaultPath: Path = Task.DEFAULT_PATH

  def outFileResource: Resource[IO, Path] =
    Resource.make {
      val tmp = Path(s"out-${UUID.randomUUID()}.txt")
      Files[IO].deleteIfExists(tmp).as(tmp)
    } { path =>
      Files[IO].deleteIfExists(path).void
    }

  sealed trait ExecuteType
  object ExecuteType {
    case object Sequential extends ExecuteType
    case object Parallel   extends ExecuteType
  }
  def calcFactorialStub(number: BigInt): FactorialResult = FactorialResult(number.toInt, number * number)
  def executeQueue(list: List[Either[ParseError, BigInt]], mode: ExecuteType): IO[List[String]] = {
    // Shutdown can destroy process with undone tasks
    def waitUntilEmpty(q: Queue[IO, ProcessMessage]): IO[Unit] = {
      def loop: IO[Unit] =
        q.size.flatMap {
          case 0 => IO.unit
          case _ => IO.sleep(10.millis) >> loop
        }
      loop
    }
    def foldSequential(queue: Queue[IO, ProcessMessage]): IO[Unit] =
      list.traverse_ {
        case Right(n) =>
          val msg = ProcessMessage.Completed(calcFactorialStub(n))
          for {
            _ <- queue.offer(msg)
            _ <- IO.sleep(20.millis)
          } yield ()

        case Left(err) =>
          for {
            _ <- queue.offer(ProcessMessage.ParseFailed(err))
            _ <- IO.sleep(20.millis)
          } yield ()
      }

    def foldParallel(queue: Queue[IO, ProcessMessage]): IO[Unit] =
      list.parTraverse_ {
        case Right(n) =>
          queue.offer(ProcessMessage.Completed(calcFactorialStub(n)))
        case Left(err) =>
          queue.offer(ProcessMessage.ParseFailed(err))
      }

    outFileResource.use { path =>
      for {
        queue <- Queue.unbounded[IO, ProcessMessage]
        fiber <- new NumberWriter[IO](path).run(queue).start

        _ <- mode match {
          case ExecuteType.Sequential => foldSequential(queue)
          case ExecuteType.Parallel   => foldParallel(queue)
        }
        _ <- waitUntilEmpty(queue)
        _ <- queue.offer(ProcessMessage.Shutdown)
        _ <- fiber.join

        lines <- Files[IO]
          .readAll(path)
          .through(fs2.text.utf8.decode)
          .through(fs2.text.lines)
          .compile
          .toList
      } yield lines
    }
  }

  test("process writes error line on ParseFailed and nothing after Shutdown") {
    outFileResource.use { path =>
      for {
        q     <- Queue.unbounded[IO, ProcessMessage]
        fiber <- new NumberWriter[IO](path).run(q).start

        err = ParseError.WrongNumberError("abc")
        _ <- q.offer(ProcessMessage.ParseFailed(err))
        _ <- q.offer(ProcessMessage.Shutdown)
        _ <- q.offer(ProcessMessage.ParseFailed(err))
        _ <- fiber.join

        lines <- Files[IO]
          .readAll(path)
          .through(fs2.text.utf8.decode)
          .through(fs2.text.lines)
          .compile
          .toList
      } yield expect(lines == List(s"${err.input} ${err.errorMessage}", ""))
    }
  }
  def expectedStubStrings(inputs: IntInputs): IO[List[String]] =
    IO.traverse(inputs.values) { x =>
      IO(calcFactorialStub(x)).map { res =>
        ProcessMessage.Completed(res).line
      }
    }

  test("process writes multiple data") {
    for {
      smallExpected <- expectedStubStrings(TestUtils.smallInputs)
      greatExpected <- expectedStubStrings(TestUtils.mediumInputsBigValues)
      r1            <- executeQueue(TestUtils.smallInputs.toEitherBigIntList, ExecuteType.Parallel)
      r2            <- executeQueue(TestUtils.smallInputs.toEitherBigIntList, ExecuteType.Sequential)
      r3            <- executeQueue(TestUtils.mediumInputsBigValues.toEitherBigIntList, ExecuteType.Parallel)
      r4            <- executeQueue(TestUtils.mediumInputsBigValues.toEitherBigIntList, ExecuteType.Sequential)
      // debug by breakpoint in checkNumberOutput or by this line:
      // _             <- IO(println(TestUtils.diffOutput(r1, smallExpected)))
    } yield expect.all(
      TestUtils.checkNumberOutput(r1, smallExpected),
      TestUtils.checkNumberOutput(r2, smallExpected),
      TestUtils.checkNumberOutput(r3, greatExpected),
      TestUtils.checkNumberOutput(r4, greatExpected)
    )
  }

  test("NumberWriter cancels with huge input") {
    outFileResource.use { path =>
      val results = TestUtils.largeInputData.values.map(x => calcFactorialStub(x))
      for {
        ref         <- Ref.of[IO, List[String]](Nil)
        queue       <- Queue.unbounded[IO, ProcessMessage]
        writerFiber <- new NumberWriter[IO](path).run(queue).start
        _           <- results.zipWithIndex.traverse_ { case (r, idx) =>
          val msg = ProcessMessage.Completed(r)
          for {
            _ <- queue.offer(msg)
            _ <- if (idx == 0) ref.update(_ :+ "started") else IO.unit
          } yield ()
        }
        _       <- TestUtils.waitForOutputsAtLeast(ref, 1)
        _       <- writerFiber.cancel
        outcome <- writerFiber.join
        lines   <- Files[IO].readAll(path)
          .through(fs2.text.utf8.decode)
          .through(fs2.text.lines)
          .compile
          .toList
      } yield {
        val canceled = outcome match {
          case cats.effect.Outcome.Canceled() => true
          case _                              => false
        }
        expect(canceled) and expect(lines.size < results.size)
      }
    }
  }

}
