package ru.hse.scala.individual

import cats.effect.std.Queue
import cats.effect.{Deferred, IO, Resource}
import cats.implicits.{toFoldableOps, toTraverseOps}
import fs2.io.file.{Files, Path}
import ru.hse.scala.individual.ParseError.NegativeNumberError
import weaver.SimpleIOSuite //1 фреймворк

import java.util.UUID
import scala.concurrent.duration.DurationInt
import scala.util.Random

object NumberWriterSpec extends SimpleIOSuite {
  val defaultPath: Path                       = Task.DEFAULT_PATH
  val smallList: List[Right[Nothing, BigInt]] =
    List(Right(BigInt(10)), Right(BigInt(20)), Right(BigInt(30)), Right(BigInt(40)))
  val greatListSmallValues: List[Right[Nothing, BigInt]] = List.fill(100)(Right(BigInt(Random.nextInt(30))))
  val greatListBigValues: List[Right[Nothing, BigInt]]   =
    List.fill(100)(Right(BigInt(Random.nextInt(1000000)))) // большие данные потребуют больше 0.2 с
  def outFileResource: Resource[IO, Path] =
    Resource.make {
      val tmp = Path(s"out-${UUID.randomUUID()}.txt")
      Files[IO].deleteIfExists(tmp).as(tmp)
    } { path =>
      Files[IO].deleteIfExists(path).void
    }

  sealed trait ExecuteType {}
  object ExecuteType       {
    case class Sequential() extends ExecuteType
    case class Parallel()   extends ExecuteType
  }

  def executeQueue(resultsList: List[Either[ParseError, BigInt]], executeType: ExecuteType): IO[List[String]] = {
    def foldSequential(queue: Queue[IO, ProcessMessage[IO]]): IO[Unit] =
      resultsList.traverse_ { r =>
        for {
          d <- Deferred[IO, Either[ParseError, BigInt]]
          _ <- queue.offer(ProcessMessage.DeferredMsg(d))
          _ <- IO.sleep(20.millis)
          _ <- d.complete(r)
          _ <- d.get
        } yield ()
      }

    def foldParallel(queue: Queue[IO, ProcessMessage[IO]]): IO[Unit] =
      for {
        deferreds <- resultsList.traverse(_ => Deferred[IO, Either[ParseError, BigInt]])
        _         <- deferreds.traverse(d => queue.offer(ProcessMessage.DeferredMsg(d)))
        _         <- deferreds.zip(resultsList).traverse { case (d, r) => d.complete(r) }
        _         <- deferreds.traverse(_.get)
      } yield ()

    outFileResource.use { path =>
      for {
        queue <- Queue.unbounded[IO, ProcessMessage[IO]]
        fiber <- new NumberWriter[IO](path).run(queue).start
        effect = executeType match {
          case ExecuteType.Sequential() => foldSequential(queue)
          case ExecuteType.Parallel()   => foldParallel(queue)
        }
        _     <- effect
        _     <- IO.sleep(200.millis)
        lines <- Files[IO]
          .readAll(path)
          .through(fs2.text.utf8.decode)
          .through(fs2.text.lines)
          .compile
          .toList
        _ <- fiber.cancel
      } yield lines
    }
  }

  test("process don't write on error") {
    outFileResource.use { path =>
      for {
        queue  <- Queue.unbounded[IO, ProcessMessage[IO]]
        d      <- Deferred[IO, Either[ParseError, BigInt]]
        fiber  <- new NumberWriter[IO](path).run(queue).start
        _      <- queue.offer(ProcessMessage.DeferredMsg(d))
        _      <- IO.sleep(200.millis)
        _      <- d.complete(Left(NegativeNumberError(-1)))
        exists <- Files[IO].exists(path)
        _      <- fiber.cancel
      } yield expect(!exists)
    }
  }
  // запустится только последний потому что IO unsafesync
  test("process write multiply data") {
    val smallExpected: List[String] =
      smallList.map(_.value.toString) ++ List("")
    val greatExpected: List[String] =
      greatListBigValues.map(_.value.toString) ++ List("")
    for {
      results1 <- executeQueue(smallList, ExecuteType.Parallel())
      exp1 = expect(smallExpected == results1)
      results2 <- executeQueue(smallList, ExecuteType.Sequential())
      exp2 = expect(smallExpected == results2)
      results3 <- executeQueue(greatListBigValues, ExecuteType.Parallel())
      exp3 = expect(greatExpected == results3)
      results4 <- executeQueue(greatListBigValues, ExecuteType.Sequential())
      exp4 = expect(greatExpected == results4)
    } yield exp1.and(exp2).and(exp3).and(exp4)
  }
}
