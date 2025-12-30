package ru.hse.scala.individual

import cats.effect.{ExitCode, IO, IOApp, Resource}
import cats.effect.std.Console
import ru.hse.scala.individual.http.{FactorialApi, HttpServerCats, ProcessingEngine}

/** HTTP entrypoint*/
object HttpTask extends IOApp {
  override def run(args: List[String]): IO[ExitCode] = {
    val host        = "0.0.0.0"
    val port        = 8080
    val parallelism = Runtime.getRuntime.availableProcessors().max(2)

    val app: Resource[IO, Unit] =
      for {
        engine <- Resource.eval(ProcessingEngine.make[IO](parallelism))
        api    <- Resource.eval(FactorialApi.make[IO](engine))
        _      <- HttpServerCats.start[IO](api.all, host, port)
      } yield ()

    app.use(_ => IO.never)
      .as(ExitCode.Success)
      .onCancel(Console[IO].println("HTTP server cancelled"))
  }
}


