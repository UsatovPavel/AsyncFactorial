package ru.hse.scala.individual

import cats.effect.std.Queue
import cats.effect.{Deferred, IO, Resource}
import fs2.io.file.{Files, Path}
import ru.hse.scala.individual.ParseError.NegativeNumberError
import weaver.SimpleIOSuite

import scala.concurrent.duration.DurationInt

object NumberWriterGenericTest extends SimpleIOSuite    {
  val defaultPath: Path = new NumberWriter[IO]().outputFilepath
  def outFileResource: Resource[IO, Path] =
    Resource.make {
      Files[IO].deleteIfExists(defaultPath).as(defaultPath)
    } { path =>
      Files[IO].deleteIfExists(path).void
    }
  //List(events)
  test("process don't write on error") {
    outFileResource.use { path =>
      for {
        _ <- IO.println("defaultPath absolute: " + java.nio.file.Paths.get("").toAbsolutePath.toString)
        existsAfterDelete <- Files[IO].exists(path)
        _ <- IO.println("existsAfterDelete: "+ existsAfterDelete)
        queue <- Queue.unbounded[IO, Deferred[IO, Either[ParseError, BigInt]]]
        dereffed <- Deferred[IO, Either[ParseError, BigInt]]
        fiber <- new NumberWriter[IO]().run(queue).start
        _ <- queue.offer(dereffed)
        existsAfterDelete <- Files[IO].exists(path)
        _ <- IO.println("existsAfterDelete: "+ existsAfterDelete)
        _ <- IO.sleep(200.millis)
        existsAfterDelete <- Files[IO].exists(path)
        _ <- IO.println("existsAfterDelete: "+ existsAfterDelete)
        _ <- dereffed.complete(Left(NegativeNumberError(-1)))
        exists <- Files[IO].exists(path) //.readAll(Path("out")).
        _ <- fiber.cancel
      } yield expect(!exists)
    }
  }
  sealed trait ExecuteType{}
  object ExecuteType {
    case class Sequential() extends ExecuteType
    case class Parallel() extends ExecuteType
  }
  def executeQueueParallel(resultsList: List[Either[ParseError, BigInt]]):IO[List[String]] = {
    for {
      _ <- Files[IO].deleteIfExists(defaultPath)
      queue <- Queue.unbounded[IO, Deferred[IO, Either[ParseError, BigInt]]]
      fiber <- new NumberWriter[IO]().run(queue).start
      _ <- resultsList.foldLeft(IO.unit) { (acc, r) =>
        acc.flatMap { _ =>
          for {
            d <- Deferred[IO, Either[ParseError, BigInt]]
            _ <- queue.offer(d)
            _ <- d.complete(r)
          } yield ()
        }
      }
      _ <- IO.sleep(200.millis)
      lines <- Files[IO]
        .readAll(Path("out.txt"))
        .through(fs2.text.utf8.decode)
        .through(fs2.text.lines)
        .compile
        .toList
      _ <-fiber.cancel
    } yield(lines)
  }

  test("process write multiply data") {
    outFileResource.use {_ =>
      val smallList = List(Right(BigInt(10)), Right(BigInt(20)), Right(BigInt(30)), Right(BigInt(40)))
      val smallExpected: List[String] = smallList.map(elem => elem.value.toString()).appended("")
      for {
        results <- executeQueueParallel(smallList)
      } yield expect(smallExpected == results)
    }
  }
}
