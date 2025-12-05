package ru.hse.scala.individual

sealed trait TestError
object TestError {
  final case class ParseFailedError(msg: String) extends TestError
  case object UnexpectedShutdownError            extends TestError
  case object NoFactorialValue                   extends TestError
}
sealed trait AppError
object AppError {
  final case class Domain(err: ParseError) extends AppError
  final case class Test(err: TestError)    extends AppError
}
