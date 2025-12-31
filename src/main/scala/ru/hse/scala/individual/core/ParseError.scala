package ru.hse.scala.individual.core

sealed trait ParseError {
  def input: String
  def errorMessage: String
}

object ParseError {
  object ErrorMessage {
    val WrongNumber    = "parse error: wrong number"
    val NegativeNumber = "parse error: negative number"
    val Unknown        = "parse error: unknown"
    val Calculation    = "parse error: calculation failed"
  }
  final case class WrongNumberError(input: String) extends ParseError {
    val errorMessage: String = ErrorMessage.WrongNumber
  }

  final case class NegativeNumberError(input: String) extends ParseError {
    val errorMessage: String = ErrorMessage.NegativeNumber
  }

  final case class UnknownError(input: String) extends ParseError {
    val errorMessage: String = ErrorMessage.Unknown
  }

  final case class CalculationError(input: String) extends ParseError {
    val errorMessage: String = ErrorMessage.Calculation
  }
}
