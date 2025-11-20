package ru.hse.scala.individual

import cats.effect.std.Queue
import cats.effect.{Deferred, IO, Ref}
import fs2.io.file.{Files, Path}
import weaver.SimpleIOSuite

import java.util.UUID

object TaskSpec extends SimpleIOSuite {

  test("taskProducer prints Exit on exit input") {
    val program = for {
      inputs  <- Ref.of[IO, List[String]](List("exit"))
      outputs <- Ref.of[IO, List[String]](List(Task.prompt))
      console  = new TestConsole[IO](inputs, outputs)
      queue   <- Queue.unbounded[IO, Deferred[IO, Either[ParseError, BigInt]]]
      writer = new NumberWriter[IO](Path("out.txt"))
      writerFiber <- writer.run(queue).start
      _ <- Task.taskProducer[IO](writerFiber, queue)(IO.asyncForIO, console)
      outVec <- outputs.get
      outcome <- writerFiber.join
      //иначе не запишет всё до вызова get
    } yield (outVec, outcome)

    program.flatMap { case (outVec, outcome) =>
      val printed = outVec.mkString("|")
      val canceledOrErrored = outcome match {
        case cats.effect.Outcome.Canceled() => true
        case cats.effect.Outcome.Errored(_) => true
        case cats.effect.Outcome.Succeeded(_) => false
      }
      IO.pure {
        expect(printed.contains("Exit")) and expect(canceledOrErrored)
      }
    }
  }
  def runTaskProducerWithFile(
                               inputsList: List[String],
                               initialOutput: List[String],
                               outputFile: Path = Path("out.txt")
                             ): IO[(List[String], List[String])] = {

    for {
      inputsRef  <- Ref.of[IO, List[String]](inputsList)
      outputsRef <- Ref.of[IO, List[String]](initialOutput)
      console     = new TestConsole[IO](inputsRef, outputsRef)
      queue      <- Queue.unbounded[IO, Deferred[IO, Either[ParseError, BigInt]]]
      writer     = new NumberWriter[IO](outputFile)
      writerFiber <- writer.run(queue).start

      _ <- Task.taskProducer[IO](writerFiber, queue)(IO.asyncForIO, console)
      outVec <- outputsRef.get

      fileContents <- Files[IO].readAll(outputFile)
        .through(fs2.text.utf8.decode)
        .compile
        .string
    } yield (outVec, fileContents.split("\n").toList)
  }
  def correctOutput(list: List[Right[Nothing, BigInt]]): List[String]  = {
    list.map(elem=>FactorialAccumulator.factorial(elem.value.intValue).toString)
  }
  def fromNumberWriterInput(list: List[Right[Nothing, BigInt]]): IO[(List[String], List[String])] = {
    val input = list.map(elem=>elem.value.toString()).appended(Task.exitCommand)
    val outputConsole = list.map(_ => Task.prompt)
    val tmp = Path(s"out-${UUID.randomUUID()}.txt")
    for {results <- runTaskProducerWithFile(input, outputConsole, tmp)
         _ <- Files[IO].deleteIfExists(tmp)//возможно удаление раньше чтения
         } yield(results)
  }
  test("taskProducer multiply output"){
    for {
      results <- fromNumberWriterInput(NumberWriterSpec.smallList)

    } yield expect(results._1==correctOutput(NumberWriterSpec.smallList))
  }
}
