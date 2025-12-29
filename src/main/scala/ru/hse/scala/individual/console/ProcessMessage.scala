package ru.hse.scala.individual.console
import ru.hse.scala.individual.core.{FactorialResult, ParseError}

sealed trait ProcessMessage {
  def render: Rendered
}

object ProcessMessage {
  final case class Completed(result: FactorialResult) extends ProcessMessage {
    def line: String     = s"${result.input} = ${result.value}"
    def render: Rendered = Rendered.Line(line)
  }
  final case class ParseFailed(error: ParseError) extends ProcessMessage {
    def line: String     = s"${error.input} ${error.errorMessage}"
    def render: Rendered = Rendered.Line(line)
  }
  case object Shutdown extends ProcessMessage {
    def render: Rendered = Rendered.Stop
  }
}

sealed trait Rendered
object Rendered {
  final case class Line(value: String) extends Rendered
  case object Stop                     extends Rendered
}
