package ru.hse.scala.individual

import cats.effect.std.Queue
import cats.effect.{IO, Resource}
import cats.implicits._
import fs2.io.file.{Files, Path}
import ru.hse.scala.individual.ParseError.NegativeNumberError
import weaver.SimpleIOSuite

import java.util.UUID
import scala.concurrent.duration._
import scala.util.Random

object NumberWriterSpec extends SimpleIOSuite {
  val defaultPath: Path                       = Task.DEFAULT_PATH
  val smallList: List[Right[Nothing, BigInt]] =
    List(Right(BigInt(10)), Right(BigInt(20)), Right(BigInt(30)), Right(BigInt(40)))
  val greatListSmallValues: List[Right[Nothing, BigInt]] = List.fill(100)(Right(BigInt(Random.nextInt(30))))
  val greatListBigValues: List[Right[Nothing, BigInt]]   =
    List.fill(100)(Right(BigInt(Random.nextInt(1000000))))

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

  test("process doesn't write on error") {
    outFileResource.use { path =>
      for {
        q      <- Queue.unbounded[IO, ProcessMessage]
        fiber  <- new NumberWriter[IO](path).run(q).start
        _      <- q.offer(ProcessMessage.ParseFailed(NegativeNumberError("-1")))
        _      <- q.offer(ProcessMessage.Shutdown)
        _      <- fiber.join
        exists <- Files[IO].exists(path)
      } yield expect(!exists)
    }
  }

  def expectedStrings(list: List[Right[Nothing, BigInt]]): List[String] =
    list.map(r => s"${r.value} = ${r.value}")

  test("process writes multiple data") {
    val smallExpected = expectedStrings(smallList)
    val greatExpected = expectedStrings(greatListBigValues)

    for {
      r1 <- executeQueue(smallList, ExecuteType.Parallel)
      r2 <- executeQueue(smallList, ExecuteType.Sequential)
      r3 <- executeQueue(greatListBigValues, ExecuteType.Parallel)
      r4 <- executeQueue(greatListBigValues, ExecuteType.Sequential)
    } yield expect.all(
      r1 == smallExpected,
      r2 == smallExpected,
      r3 == greatExpected,
      r4 == greatExpected
    )
  }
}
