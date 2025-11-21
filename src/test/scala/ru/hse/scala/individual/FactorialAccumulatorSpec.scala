package ru.hse.scala.individual

import cats.effect.unsafe.implicits.global
import cats.effect.{Deferred, IO}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import ru.hse.scala.individual.FactorialAccumulator.factorial
import ru.hse.scala.individual.ParseError.{NegativeNumberError, WrongNumberError}

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
  def findDeferred(text: String): IO[Either[ParseError, BigInt]] = {
    for {
      deferred <- Deferred[IO, Either[ParseError, BigInt]]
      _        <- FactorialAccumulator.inputNumber(text, deferred)
      result   <- deferred.get
    } yield result
  }
  "inputResult" should "be one for '0 '" in {
    val result = findDeferred("0 ").unsafeRunSync()
    result shouldBe Right(BigInt(1))
  }

  it should "be 120 for '   5  '" in {
    val result = findDeferred("   5  ").unsafeRunSync()
    result shouldBe Right(BigInt(120))
  }
  it should "be 3628800 for '   10  '" in {
    val result = findDeferred("   10  ").unsafeRunSync()
    result shouldBe Right(BigInt(3628800))
  }
  it should "be parseError for '22222222222222', '  1 1', 'a', '-1'" in {
    findDeferred("22222222222222").unsafeRunSync() shouldBe Left(WrongNumberError("22222222222222"))
    findDeferred("  1 1").unsafeRunSync() shouldBe Left(WrongNumberError("  1 1"))
    findDeferred("a").unsafeRunSync() shouldBe Left(WrongNumberError("a"))
    findDeferred("-1").unsafeRunSync() shouldBe Left(NegativeNumberError(-1))
  }
}
