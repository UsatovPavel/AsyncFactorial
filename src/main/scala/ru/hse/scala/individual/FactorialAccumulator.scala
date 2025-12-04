package ru.hse.scala.individual

import cats.effect.std.Queue
import ru.hse.scala.individual.ParseError.{CalculationError, NegativeNumberError, WrongNumberError}

import scala.annotation.tailrec

final case class FactorialResult(input: Int, value: BigInt)

class FactorialAccumulator {}

object FactorialAccumulator {

  def inputNumber[F[_]](line: String, out: Queue[F, ProcessMessage]): F[Unit] = {
    val maybeNumber = line.trim.toIntOption
    maybeNumber match {
      case None =>
        out.offer(ProcessMessage.ParseFailed(WrongNumberError(line)))
      case Some(number) =>
        if (number < 0) {
          out.offer(ProcessMessage.ParseFailed(NegativeNumberError(line)))
        } else {
          val optionalFactorial = factorial(number)
          optionalFactorial match {
            case None         => out.offer(ProcessMessage.ParseFailed(CalculationError(line)))
            case Some(result) => out.offer(ProcessMessage.Completed(FactorialResult(number, result)))
          }
        }
    }
  }
  // функция должна быть надёжной даже при использовании вне контекста
  def factorial(n: Int): Option[BigInt] = {
    @tailrec
    def recursion(n: Int, acc: BigInt): Option[BigInt] = n match {
      case 0 => Option(acc)
      case _ => recursion(n - 1, acc * n)
    }
    if (n < 0) Option.empty else recursion(n, 1)
  }
}
