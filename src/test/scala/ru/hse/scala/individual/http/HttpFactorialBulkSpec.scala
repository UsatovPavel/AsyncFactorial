package ru.hse.scala.individual.http

import cats.effect.unsafe.implicits.global
import cats.effect.{IO, Resource}
import io.circe.generic.auto._
import io.circe.parser.decode
import io.circe.syntax._
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers
import ru.hse.scala.individual.core.ParseError
import sttp.client4._
import sttp.client4.httpclient.cats.HttpClientCatsBackend

final class HttpFactorialBulkSpec extends AsyncFlatSpec with Matchers {

  private def withClientAndServer[A](use: (Backend[IO], Int) => IO[A]): IO[A] = {
    val host = "127.0.0.1"
    val port = 0 // OS pick a free port

    val resources: Resource[IO, (Backend[IO], Int)] =
      for {
        backend <- HttpClientCatsBackend.resource[IO]()
        engine  <- Resource.eval(ProcessingEngine.make[IO](parallelism = 4))
        api     <- Resource.eval(FactorialApi.make[IO](engine))
        server  <- HttpServerCats.start[IO](api.all, host, port)
      } yield (backend, server.actualPort())

    resources.use { case (backend, actualPort) => use(backend, actualPort) }
  }

  behavior of "POST /factorial (bulk)"

  it should "return N items with stable jobId and itemId coverage for a large input" in {
    withClientAndServer { (backend, port) =>
      val n       = 2000
      val jobId   = "fixed"
      val inputs  = List.tabulate(n) { idx => if (idx % 10 == 0) -1 else 10 }
      val uriBase = uri"http://127.0.0.1:$port/factorial"

      val request = basicRequest
        .post(uriBase)
        .header("X-Job-Id", jobId)
        .contentType("application/json")
        .body(inputs.asJson.noSpaces)
        .response(asStringAlways)

      request.send(backend).flatMap { resp =>
        if (!resp.code.isSuccess) IO.raiseError(new RuntimeException(s"HTTP ${resp.code}: ${resp.body}"))
        else IO.fromEither(decode[List[ResultItemDto]](resp.body))
      }.map { items =>
        items.size shouldBe n
        all(items.map(_.jobId)) shouldBe jobId

        val byItemId = items.groupBy(_.itemId)
        byItemId.keySet shouldBe (0L until n.toLong).toSet
        byItemId.values.foreach(_.size shouldBe 1)

        // Validate each item according to input
        items.foreach { item =>
          // exactly one of result/error
          (item.result.isDefined ^ item.error.isDefined) shouldBe true

          if (item.itemId % 10 == 0L) {
            item.input shouldBe -1
            item.error shouldBe Some(ParseError.ErrorMessage.NegativeNumber)
            item.result shouldBe None
          } else {
            item.input shouldBe 10
            item.result shouldBe Some("3628800")
            item.error shouldBe None
          }
        }
        succeed
      }
    }.unsafeToFuture()
  }
}
