package ru.hse.scala.individual

import scala.util.Random

object TestUtils {
  def checkNumberOutput(result: List[String], expected: List[String]): Boolean =
    result.nonEmpty &&
      result.last == "" &&
      result.dropRight(1).sorted == expected.sorted
  def checkWithoutLastOutput(result: List[String], expected: List[String]): Boolean =
    result.sorted == expected.sorted
  val smallList: List[Right[Nothing, BigInt]] =
    List(Right(BigInt(10)), Right(BigInt(20)), Right(BigInt(30)), Right(BigInt(40)))
  val mediumListSmallValues: List[Right[Nothing, BigInt]] = List.fill(100)(Right(BigInt(Random.nextInt(30))))
  val mediumListBigValues: List[Right[Nothing, BigInt]]   =
    List.fill(100)(Right(BigInt(Random.nextInt(1000000))))
  val bigListSmallValues: List[Right[Nothing, BigInt]] = List.fill(10000)(Right(BigInt(Random.nextInt(30))))
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
      missing.foreach(s => builder.append(s"  + $s\n"))
    }

    if (excessive.nonEmpty) {
      builder.append("Excessive lines:\n")
      excessive.foreach(s => builder.append(s"  - $s\n"))
    }

    if (zipCompare.nonEmpty) {
      builder.append("Different at sorted positions:\n")
      zipCompare.foreach(diff => builder.append(diff + "\n"))
    }

    if (missing.isEmpty && excessive.isEmpty && zipCompare.isEmpty)
      builder.append("No differences (sorted lists match)\n")

    builder.append("=== DIFF END ===")

    builder.toString()
  }

}
//Дальнейший план: нужно тесты для TaskSpec добавить больше чисел(сейчас 100, нужно на 10000 потестить)
//Вынести в TestUtils input и expected
//Удалить WaitGroup потому что сейчас не используется и some() с тестом print в Task
//Добавить тест на Ctrl+Z отмену Task и то что все fiber отменяются
//В NumberWriterSpec Отдельно тест что NumberWriter(waitForAll=false) при отмене завершает все свои fiber
//На вход данные из 10000

//В taskSpec - В случае wait all что всё дописывается(накидать input на 10000, и отменить task, ждать)

//Сейчас  в NumberWriter def expectedStrings(list: List[Right[Nothing, BigInt]]): List[String] =
//    list.map(r => s"${r.value} = ${r.value}")
// некорректно: нужно в testUtils заменить массивы данных на особый тип данных - обёртка над List[Int], у которого будет метод
//getFactorialResult который будет получать List[FactorialResult]( через вызов Factorial)
// И затем уже использовать getFactorialResult в тестах в качестве данных для проверки того что записал NumberWriter/Task
// которые будут поступать в NumberWriter
//Анализ проекта нужно сделать на выполнение требований. Думать
//Критические недостатки устранить
