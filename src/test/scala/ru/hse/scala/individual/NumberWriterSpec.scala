package ru.hse.scala.individual

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

  def executeQueue(list: List[Either[ParseError, BigInt]], mode: ExecuteType): IO[List[String]] = {
    // Shutdown can destroy process with undone tasks
    def waitUntilEmpty(q: Queue[IO, ProcessMessage]): IO[Unit] = {
      def loop: IO[Unit] =
        q.size.flatMap {
          case 0 => IO.unit
          case _ => loop
        }
      loop
    }
    def foldSequential(queue: Queue[IO, ProcessMessage]): IO[Unit] =
      list.traverse_ {
        case Right(n) =>
          val msg = ProcessMessage.Completed(FactorialResult(n.toInt, n))
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
          queue.offer(ProcessMessage.Completed(FactorialResult(n.toInt, n)))
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

  def expectedStrings(list: List[Right[Nothing, BigInt]]): List[String] =
    list.map(r => s"${r.value} = ${r.value}")

  test("process writes multiple data") {
    val smallExpected = expectedStrings(TestUtils.smallList)
    val greatExpected = expectedStrings(TestUtils.mediumListBigValues)

    for {
      r1 <- executeQueue(TestUtils.smallList, ExecuteType.Parallel)
      r2 <- executeQueue(TestUtils.smallList, ExecuteType.Sequential)
      r3 <- executeQueue(TestUtils.mediumListBigValues, ExecuteType.Parallel)
      r4 <- executeQueue(TestUtils.mediumListBigValues, ExecuteType.Sequential)
    } yield expect.all(
      TestUtils.checkNumberOutput(r1, smallExpected),
      TestUtils.checkNumberOutput(r2, smallExpected),
      TestUtils.checkNumberOutput(r3, greatExpected),
      TestUtils.checkNumberOutput(r4, greatExpected)
    )
  }

}
