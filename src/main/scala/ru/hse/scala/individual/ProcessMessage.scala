package ru.hse.scala.individual

import cats.effect.Deferred

sealed trait ProcessMessage[F[_]]
object ProcessMessage {
  final case class Shutdown[F[_]]() extends ProcessMessage[F]
  final case class DeferredMsg[F[_]](deferred: Deferred[F, Either[ParseError, BigInt]])
    extends ProcessMessage[F]
}
