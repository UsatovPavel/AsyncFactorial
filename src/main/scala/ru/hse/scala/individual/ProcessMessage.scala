package ru.hse.scala.individual

sealed trait ProcessMessage

object ProcessMessage {
  final case class Completed(result: FactorialResult) extends ProcessMessage
  final case class ParseFailed(error: ParseError)     extends ProcessMessage
  case object Shutdown                                extends ProcessMessage
}
