package ru.hse.scala.individual

import cats.effect.{IO, Ref}
import ru.hse.scala.individual.console.IntInputs

import scala.concurrent.duration.DurationInt
import scala.util.Random

object TestUtils {

  def checkNumberOutput(result: List[String], expected: List[String]): Boolean =
    result.nonEmpty &&
      result.lastOption.contains("") &&
      result.dropRight(1).sorted == expected.sorted
  def checkWithoutLastOutput(result: List[String], expected: List[String]): Boolean =
    result.sorted == expected.sorted

  val smallInputs: IntInputs             = IntInputs(List(10, 20, 30, 40))
  val mediumInputsSmallValues: IntInputs = IntInputs(List.fill(100)(Random.nextInt(30)))
  val mediumInputsBigValues: IntInputs   = IntInputs(List.fill(100)(Random.nextInt(1000000)))
  val largeInputData: IntInputs          = IntInputs(List.fill(10000)(Random.nextInt(30)))
  val veryLargeInputData: IntInputs      = IntInputs(List.fill(100000)(Random.nextInt(30)))

  def waitForOutputsAtLeast(ref: Ref[IO, List[String]], n: Int): IO[Unit] = {
    def loop: IO[Unit] =
      ref.get.flatMap { xs =>
        if (xs.size >= n) IO.unit
        else IO.sleep(10.millis) >> loop
      }
    loop
  }
  def diffOutput(result: List[String], expected: List[String]): String = {
    val rSorted = result.sorted
    val eSorted = expected.sorted

    val missing   = eSorted.diff(rSorted)
    val excessive = rSorted.diff(eSorted)

    val zipCompare =
      rSorted
        .zipAll(eSorted, "<missing>", "<missing>")
        .zipWithIndex
        .collect {
          case ((r, e), idx) if r != e =>
            s"  $idx:\n    result:   [$r]\n    expected: [$e]"
        }

    val builder = new StringBuilder
    builder.append("=== DIFF START ===\n")

    if (missing.nonEmpty) {
      builder.append("Missing lines:\n")
      missing.foldLeft(builder)((b, s) => b.append(s"  + $s\n"))
    }

    if (excessive.nonEmpty) {
      builder.append("Excessive lines:\n")
      excessive.foldLeft(builder)((b, s) => b.append(s"  - $s\n"))
    }

    if (zipCompare.nonEmpty) {
      builder.append("Different at sorted positions:\n")
      zipCompare.foldLeft(builder)((b, d) => b.append(d + "\n"))
    }

    if (missing.isEmpty && excessive.isEmpty && zipCompare.isEmpty)
      builder.append("No differences (sorted lists match)\n")

    builder.append("=== DIFF END ===")
    builder.toString()
  }
}
