package ru.hse.scala.individual

import cats.effect.Sync
import cats.effect.std.Queue
import ru.hse.scala.individual.ParseError.{NegativeNumberError, UnknownError, WrongNumberError}

import scala.annotation.tailrec
import scala.concurrent.Promise

sealed trait ParseError
object ParseError {
  case class WrongNumberError(actual: String) extends ParseError
  case class NegativeNumberError(number: Int) extends ParseError
  object UnknownError extends ParseError
}

class FactorialAccumulator {
}

object FactorialAccumulator {
  def processInput[F[_]: Sync](line: String, promise: Promise[Either[ParseError, BigInt]])= {
    val maybeNumber = line.trim.toIntOption
    maybeNumber match {
      case None => promise.success(Left(WrongNumberError(line)))
      case Some(number)=> {
        if (number<0){
          promise.success(Left(NegativeNumberError(number)))
        } else {
          val optionalFactorial = factorial(number)
          optionalFactorial match {
            case None => promise.success(Left(UnknownError))
            case Some(result)=>promise.success(Right(result))
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
