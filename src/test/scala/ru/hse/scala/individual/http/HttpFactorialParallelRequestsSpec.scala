package ru.hse.scala.individual.http

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.syntax.all._
import io.circe.generic.auto._
import io.circe.parser.decode
import io.circe.syntax._
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.client4._

import scala.concurrent.duration._

final class HttpFactorialParallelRequestsSpec extends AsyncFlatSpec with Matchers {

  behavior of "POST /factorial (parallel requests)"

  it should "handle 100 parallel requests without mixing jobIds" in {
    HttpTestSupport.withClientAndServer(engineParallelism = 8) { (backend, port) =>
      val requestCount   = 100
      val payloadSize    = 700
      val negativeEvery  = 17

      def inputsFor(reqIdx: Int): List[Int] =
        List.tabulate(payloadSize) { i =>
          if ((i + reqIdx) % negativeEvery == 0) -1 else 10
        }

      def sendOne(reqIdx: Int): IO[Unit] = {
        val jobId  = s"job-$reqIdx"
        val inputs = inputsFor(reqIdx)

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
          items.size shouldBe payloadSize
          all(items.map(_.jobId)) shouldBe jobId

          // itemId coverage and 1-1 mapping
          val byItemId = items.groupBy(_.itemId)
          byItemId.keySet shouldBe (0L until payloadSize.toLong).toSet
          byItemId.values.foreach(_.size shouldBe 1)

          // Validate per itemId according to our generator
          items.foreach { item =>
            (item.result.isDefined ^ item.error.isDefined) shouldBe true
            val expectedInput = inputs(item.itemId.toInt)
            item.input shouldBe expectedInput
          }
        }
      }

      List
        .range(0, requestCount)
        .parTraverse_(sendOne)
        .timeout(30.seconds)
        .as(succeed)
    }.unsafeToFuture()
  }
}


