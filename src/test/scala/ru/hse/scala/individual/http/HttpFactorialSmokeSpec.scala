package ru.hse.scala.individual.http

import cats.effect.unsafe.implicits.global
import cats.effect.IO
import io.circe.parser.decode
import io.circe.syntax._
import io.circe.generic.auto._
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.client4._

final class HttpFactorialSmokeSpec extends AsyncFlatSpec with Matchers {

  behavior of "POST /factorial (smoke)"

  it should "return one Success item for [5]" in {
    HttpTestSupport.withClientAndServer(engineParallelism = 2) { (backend, port) =>
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

        succeed
      }
    }.unsafeToFuture()
  }
}


