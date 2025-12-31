package ru.hse.scala.individual.http

import sttp.tapir._
import sttp.tapir.json.tethys._

object FactorialEndpoints {
  // Input order matters for the tuple type: (jobIdHeader, numbers)
  val factorial: PublicEndpoint[(Option[String], List[Int]), String, List[ResultItemDto], Any] =
    endpoint.post
      .in("factorial")
      .in(header[Option[String]]("X-Job-Id"))
      .in(jsonBody[List[Int]])
      .out(jsonBody[List[ResultItemDto]])
      .errorOut(stringBody)
}
