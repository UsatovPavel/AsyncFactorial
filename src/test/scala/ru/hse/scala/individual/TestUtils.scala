package ru.hse.scala.individual

import scala.util.Random

object TestUtils {

  def checkNumberOutput(result: List[String], expected: List[String]): Boolean =
    result.nonEmpty &&
      result.lastOption.contains("") &&
      result.dropRight(1).sorted == expected.sorted
  def checkWithoutLastOutput(result: List[String], expected: List[String]): Boolean =
    result.sorted == expected.sorted

  val smallList: List[Right[Nothing, BigInt]] =
    List(Right(BigInt(10)), Right(BigInt(20)), Right(BigInt(30)), Right(BigInt(40)))
  val mediumListSmallValues: List[Right[Nothing, BigInt]] = List.fill(100)(Right(BigInt(Random.nextInt(30))))
  val mediumListBigValues: List[Right[Nothing, BigInt]]   = List.fill(100)(Right(BigInt(Random.nextInt(1000000))))
  val bigListSmallValues: List[Right[Nothing, BigInt]]    = List.fill(10000)(Right(BigInt(Random.nextInt(30))))

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
