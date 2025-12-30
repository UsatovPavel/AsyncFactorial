package ru.hse.scala.individual

import cats.effect.{ExitCode, IO, IOApp, Resource}
import cats.effect.std.Console
import ru.hse.scala.individual.http.{FactorialApi, HttpServerCats, ProcessingEngine, ServerConfig}

/** HTTP entrypoint */
object HttpTask extends IOApp {
  override def run(args: List[String]): IO[ExitCode] = {
    val config = ServerConfig.loadOrThrow()

    val app: Resource[IO, Unit] =
      for {
        engine <- Resource.eval(ProcessingEngine.make[IO](config.parallelism))
        api    <- Resource.eval(FactorialApi.make[IO](engine))
        _      <- HttpServerCats.start[IO](api.all, config.host, config.port)
      } yield ()

    app.use(_ => IO.never)
      .as(ExitCode.Success)
      .onCancel(Console[IO].println("HTTP server cancelled"))
  }
}
