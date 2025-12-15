package ru.hse.scala.individual

import cats.effect.Sync
import cats.effect.std.Queue
import cats.implicits.toFlatMapOps
import ru.hse.scala.individual.ParseError.{CalculationError, NegativeNumberError, WrongNumberError}

import scala.annotation.tailrec

/** Результат вычисления факториала конкретного числа. */
final case class FactorialResult(input: Int, value: BigInt)

class FactorialAccumulator {}

/** Отвечает за разбор ввода, вычисление факториалов и публикацию результата в очередь. */
object FactorialAccumulator {

  def inputNumber[F[_]: Sync](line: String, out: Queue[F, ProcessMessage]): F[Unit] = {
    val maybeNumber = line.trim.toIntOption
    maybeNumber match {
      case None =>
        out.offer(ProcessMessage.ParseFailed(WrongNumberError(line)))
      case Some(number) =>
        if (number < 0) { // guard
          out.offer(ProcessMessage.ParseFailed(NegativeNumberError(line)))
        } else {
          Sync[F].delay(factorial(number)).flatMap {
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
