package ru.hse.scala.individual.http

import cats.effect.unsafe.implicits.global
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.client4._

final class HttpFactorialInvalidJsonSpec extends AsyncFlatSpec with Matchers {

  behavior of "POST /factorial (invalid json)"

  it should "return 400 for invalid JSON body" in {
    HttpTestSupport.withClientAndServer(engineParallelism = 2) { (backend, port) =>
      val invalidJson = "[1,]" // invalid JSON

      val request = basicRequest
        .post(uri"http://127.0.0.1:$port/factorial")
        .contentType("application/json")
        .body(invalidJson)
        .response(asStringAlways)

      request.send(backend).map { resp =>
        resp.code.code shouldBe 400
        succeed
      }
    }.unsafeToFuture()
  }
}
