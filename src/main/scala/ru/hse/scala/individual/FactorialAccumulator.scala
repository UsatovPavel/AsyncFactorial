package ru.hse.scala.individual

import cats.effect.Deferred
import ru.hse.scala.individual.ParseError.{NegativeNumberError, UnknownError, WrongNumberError}

import scala.annotation.tailrec

sealed trait ParseError
object ParseError {
  case class WrongNumberError(actual: String) extends ParseError
  case class NegativeNumberError(number: Int) extends ParseError
  object UnknownError extends ParseError
}

class FactorialAccumulator {
}

object FactorialAccumulator {

  def inputNumber[F[_]](line: String, deferred: Deferred[F, Either[ParseError, BigInt]]): F[Boolean] = {
    val maybeNumber = line.trim.toIntOption
    maybeNumber match {
      case None => deferred.complete(Left(WrongNumberError(line)))
      case Some(number)=> {
        if (number<0){
          deferred.complete(Left(NegativeNumberError(number)))
        } else {
          val optionalFactorial = factorial(number)
          optionalFactorial match {
            case None => deferred.complete(Left(UnknownError))
            case Some(result)=>deferred.complete(Right(result))
          }
          }
        }
      }
    }
  //функция должна быть надёжной даже при использовании вне контекста
  def factorial(n: Int): Option[BigInt] = {
    @tailrec
    def recursion(n: Int, acc: BigInt): Option[BigInt] = n match {
      case 0 => Option(acc)
      case _ => recursion(n - 1, acc * n)
    }
    if (n < 0) Option.empty else recursion(n, 1)
  }
}
