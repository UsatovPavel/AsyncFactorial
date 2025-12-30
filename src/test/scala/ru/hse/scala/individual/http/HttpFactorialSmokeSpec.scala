package ru.hse.scala.individual.http

import cats.effect.{IO, Resource}
import cats.effect.unsafe.implicits.global
import io.circe.parser.decode
import io.circe.syntax._
import io.circe.generic.auto._
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.client4._
import sttp.client4.httpclient.cats.HttpClientCatsBackend

final class HttpFactorialSmokeSpec extends AsyncFlatSpec with Matchers {

  private def withClientAndServer[A](use: (Backend[IO], Int) => IO[A]): IO[A] = {
    val host = "127.0.0.1"
    val port = 0 // let OS pick a free port

    val resources: Resource[IO, (Backend[IO], Int)] =
      for {
        backend <- HttpClientCatsBackend.resource[IO]()
        engine  <- Resource.eval(ProcessingEngine.make[IO](parallelism = 2))
        api     <- Resource.eval(FactorialApi.make[IO](engine))
        server  <- HttpServerCats.start[IO](api.all, host, port)
      } yield (backend, server.actualPort())

    resources.use { case (backend, actualPort) => use(backend, actualPort) }
  }

  behavior of "POST /factorial (smoke)"

  it should "return one Success item for [5]" in {
    withClientAndServer { (backend, port) =>
      val inputs = List(5)

      val request = basicRequest
        .post(uri"http://127.0.0.1:$port/factorial")
        .contentType("application/json")
        .body(inputs.asJson.noSpaces)
        .response(asStringAlways)

      request.send(backend).flatMap { resp =>
        if (!resp.code.isSuccess) IO.raiseError(new RuntimeException(s"HTTP ${resp.code}: ${resp.body}"))
        else IO.fromEither(decode[List[ResultItemDto]](resp.body))
      }.map { items =>
        items.size shouldBe 1

        val item = items.head
        item.jobId.trim.nonEmpty shouldBe true
        item.itemId shouldBe 0L
        item.input shouldBe 5
        item.result shouldBe Some("120")
        item.error shouldBe None
      }
    }.unsafeToFuture()
  }
}
