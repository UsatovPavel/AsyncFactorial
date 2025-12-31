package ru.hse.scala.individual.http

import cats.effect.Async
import cats.syntax.all._
import sttp.capabilities.WebSockets
import sttp.capabilities.fs2.Fs2Streams
import sttp.tapir.server.ServerEndpoint

import java.util.UUID

final class FactorialApi[F[_]: Async](
    engine: ProcessingEngine[F]
) {
  private def resolveJobId(jobIdHeader: Option[String]): F[String] =
    jobIdHeader.map(_.trim).filter(_.nonEmpty) match {
      case Some(value) => Async[F].pure(value)
      case None        => Async[F].delay(UUID.randomUUID().toString)
    }

  private def toTaskItems(jobId: String, inputs: List[Int]): List[TaskItem] =
    inputs.zipWithIndex.map { case (n, idx) => TaskItem(jobId, idx.toLong, n) }

  val factorial: ServerEndpoint[Fs2Streams[F] with WebSockets, F] =
    FactorialEndpoints.factorial
      .serverLogicSuccess { case (jobIdHeader, inputs) =>
        for {
          jobId <- resolveJobId(jobIdHeader)
          items = toTaskItems(jobId, inputs)
          out   <- engine.run(items)
        } yield out.map(ResultItemDto.from)
      }

  def all: List[ServerEndpoint[Fs2Streams[F] with WebSockets, F]] =
    List(factorial)
}

object FactorialApi {
  def make[F[_]: Async](engine: ProcessingEngine[F]): F[FactorialApi[F]] =
    Async[F].pure(new FactorialApi[F](engine))
}


