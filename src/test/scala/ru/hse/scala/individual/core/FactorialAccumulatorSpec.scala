package ru.hse.scala.individual.core

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import ru.hse.scala.individual.console.ProcessMessage
import ru.hse.scala.individual.core.FactorialAccumulator.factorial
import ru.hse.scala.individual.core.ParseError.{NegativeNumberError, WrongNumberError}

class FactorialAccumulatorSpec extends AnyFlatSpec with Matchers {
  "factorial" should "return None for negative number" in {
    factorial(-1) shouldBe None
    factorial(Int.MinValue) shouldBe None
  }

  it should "be one for zero" in {
    factorial(0) shouldBe Some(BigInt(1))
  }

  it should "be 120 for 5" in {
    factorial(5) shouldBe Some(BigInt(120))
  }

  it should "be 3628800 for 10" in {
    factorial(10) shouldBe Some(BigInt(3628800))
  }
  def parseFactorialMessage(text: String): IO[Either[AppError, FactorialResult]] = {
    for {
      queue   <- cats.effect.std.Queue.unbounded[IO, ProcessMessage]
      _       <- FactorialAccumulator.inputNumber(text, queue)
      message <- queue.take
    } yield message match {
      case ProcessMessage.ParseFailed(err)  => Left(AppError.Domain(err))
      case ProcessMessage.Completed(result) => Right(result)
      case ProcessMessage.Shutdown          => Left(AppError.Test(TestError.UnexpectedShutdownError))
    }
  }
  "inputResult" should "be one for '0 '" in {
    val result = parseFactorialMessage("0 ").unsafeRunSync()
    result shouldBe Right(FactorialResult(0, BigInt(1)))
  }

  it should "be 120 for '   5  '" in {
    val result = parseFactorialMessage("   5  ").unsafeRunSync()
    result shouldBe Right(FactorialResult(5, BigInt(120)))
  }

  it should "be 3628800 for '   10  '" in {
    val result = parseFactorialMessage("   10  ").unsafeRunSync()
    result shouldBe Right(FactorialResult(10, BigInt(3628800)))
  }

  it should "be parseError for '22222222222222', '  1 1', 'a', '-1'" in {
    parseFactorialMessage("22222222222222").unsafeRunSync() shouldBe Left(
      AppError.Domain(WrongNumberError("22222222222222"))
    )
    parseFactorialMessage("  1 1").unsafeRunSync() shouldBe Left(AppError.Domain(WrongNumberError("  1 1")))
    parseFactorialMessage("a").unsafeRunSync() shouldBe Left(AppError.Domain(WrongNumberError("a")))
    parseFactorialMessage("-1").unsafeRunSync() shouldBe Left(AppError.Domain(NegativeNumberError("-1")))
  }
}
