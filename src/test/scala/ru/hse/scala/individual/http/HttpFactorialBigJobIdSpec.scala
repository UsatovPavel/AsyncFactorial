package ru.hse.scala.individual.http

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.circe.generic.auto._
import io.circe.parser.decode
import io.circe.syntax._
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.client4._

final class HttpFactorialBigJobIdSpec extends AsyncFlatSpec with Matchers {

  behavior of "POST /factorial (big job id)"

  it should "accept a large X-Job-Id and echo it in all response items" in {
    HttpTestSupport.withClientAndServer(engineParallelism = 2) { (backend, port) =>
      // Keep it large but still within typical header limits.
      val jobId  = "j" * 4096
      val inputs = List(5, 10, -1)

      val request = basicRequest
        .post(uri"http://127.0.0.1:$port/factorial")
        .header("X-Job-Id", jobId)
        .contentType("application/json")
        .body(inputs.asJson.noSpaces)
        .response(asStringAlways)

      request.send(backend).flatMap { resp =>
        if (!resp.code.isSuccess) IO.raiseError(new RuntimeException(s"HTTP ${resp.code}: ${resp.body}"))
        else IO.fromEither(decode[List[ResultItemDto]](resp.body))
      }.map { items =>
        items.size shouldBe inputs.size
        all(items.map(_.jobId)) shouldBe jobId

        succeed
      }
    }.unsafeToFuture()
  }
}


