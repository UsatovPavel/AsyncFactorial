package ru.hse.scala.individual

import cats.effect.std.Queue
import cats.effect.{Deferred, IO, Resource}
import ru.hse.scala.individual.ParseError.NegativeNumberError
import weaver.IOSuite

import java.nio.charset.StandardCharsets
import java.nio.file.{Files => JFiles, Path => JPath}
import scala.concurrent.duration._
import scala.jdk.CollectionConverters._

class NumberWriterSpec extends IOSuite {
  override type Res = JPath

  override def sharedResource: Resource[IO, JPath] = {
    Resource.make(
      IO {
        val prev = System.getProperty("user.dir")
        val dir = JFiles.createTempDirectory("number-writer-test")
        System.setProperty("user.dir", dir.toString)
        (dir, prev)
      }
    ) { case (dir, prev) =>
      IO {
        System.setProperty("user.dir", prev)
        val stream = JFiles.walk(dir).iterator().asScala.toList.reverse
        stream.foreach(p => JFiles.deleteIfExists(p))
      }
    }.map(_._1)
  }

  test("process writes number to out.txt on Right") { dir =>
    for {
      queue <- Queue.unbounded[IO, Deferred[IO, Either[ParseError, BigInt]]]
      writer = new NumberWriter[IO]()
      deferred <- Deferred[IO, Either[ParseError, BigInt]]
      fiber <- writer.run(queue).start
      _ <- queue.offer(deferred)
      _ <- deferred.complete(Right(BigInt(42)))
      _ <- IO.sleep(200.millis)
      exists <- IO(JFiles.exists(dir.resolve("out.txt")))
      content <- IO {
        if (exists) new String(JFiles.readAllBytes(dir.resolve("out.txt")), StandardCharsets.UTF_8) else ""
      }
      _ <- fiber.cancel
    } yield expect(exists && content.trim == "42")
  }

  test("process ignores Left and does not create out.txt") { dir =>
    for {
      queue <- Queue.unbounded[IO, Deferred[IO, Either[ParseError, BigInt]]]
      writer = new NumberWriter[IO]()
      deferred <- Deferred[IO, Either[ParseError, BigInt]]
      fiber <- writer.run(queue).start
      _ <- queue.offer(deferred)
      _ <- deferred.complete(Left(NegativeNumberError(-1)))
      _ <- IO.sleep(200.millis)
      exists <- IO(JFiles.exists(dir.resolve("out.txt")))
      _ <- fiber.cancel
    } yield expect(!exists)
  }
}
