package ru.hse.scala.individual.http

import cats.effect.std.{Console, Dispatcher}
import cats.effect.{Async, Resource, Sync}
import cats.syntax.functor._
import io.vertx.core.Vertx
import io.vertx.core.http.HttpServer
import io.vertx.ext.web.Router
import sttp.capabilities.WebSockets
import sttp.capabilities.fs2.Fs2Streams
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.vertx.cats.VertxCatsServerInterpreter.VertxFutureToCatsF
import sttp.tapir.server.vertx.cats.{VertxCatsServerInterpreter, VertxCatsServerOptions}
import sttp.tapir.swagger.bundle.SwaggerInterpreter

/** Minimal tapir+vertx server wiring (based on http-sample, but simplified to one server/zone). */
object HttpServerCats {

  def start[F[_]: Async: Console](
      endpoints: List[ServerEndpoint[Fs2Streams[F] with WebSockets, F]],
      host: String,
      port: Int,
  ): Resource[F, HttpServer] =
    for {
      dispatcher <- Dispatcher.parallel[F]
      options: VertxCatsServerOptions[F] =
        VertxCatsServerOptions
          .customiseInterceptors[F](dispatcher)
          .options

      docsEndpoints = SwaggerInterpreter()
        .fromServerEndpoints[F](endpoints, "factorial-service", "1.0")
      allEndpoints = endpoints ++ docsEndpoints

      vertx <- Resource.make(Sync[F].delay(Vertx.vertx()))(_.close().asF[F].void)

      server <- Resource.make {
        Sync[F].defer {
          val httpServer = vertx.createHttpServer()
          val router     = Router.router(vertx)
          allEndpoints.foreach(
            VertxCatsServerInterpreter(options)
              .route(_)
              .apply(router)
          )
          httpServer
            .requestHandler(router)
            .listen(port, host)
            .asF[F]
        }
      }(_.close().asF[F].void)

      _ <- Resource.eval(Console[F].println(s"HTTP server started on http://$host:${server.actualPort()}/"))
    } yield server
}


