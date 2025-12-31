package ru.hse.scala.individual.http

import cats.effect.{IO, Resource}
import sttp.client4.Backend
import sttp.client4.httpclient.cats.HttpClientCatsBackend

object HttpTestSupport {
  def withClientAndServer[A](
      engineParallelism: Int
  )(
      use: (Backend[IO], Int) => IO[A]
  ): IO[A] = {
    val host = "127.0.0.1"
    val port = 0 // let OS pick a free port

    val resources: Resource[IO, (Backend[IO], Int)] =
      for {
        backend <- HttpClientCatsBackend.resource[IO]()
        engine  <- Resource.eval(ProcessingEngine.make[IO](parallelism = engineParallelism))
        api     <- Resource.eval(FactorialApi.make[IO](engine))
        server  <- HttpServerCats.start[IO](api.all, host, port)
      } yield (backend, server.actualPort())

    resources.use { case (backend, actualPort) => use(backend, actualPort) }
  }
}


