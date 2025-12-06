package ru.hse.scala.individual

import cats.effect._
import cats.effect.std.{Queue, Supervisor}
import fs2.io.file.Files
import weaver.SimpleIOSuite

object TaskProducerSpec extends SimpleIOSuite {

  test("producer abrupt cancel then writer finishes after Shutdown") {
    for {
      tmpPath    <- Files[IO].createTempFile(None, "cancel-", ".txt", None)
      inputsRef  <- Ref.of[IO, List[String]](TestUtils.largeInputData.values.map(_.toString))
      outputsRef <- Ref.of[IO, List[String]](List.empty)
      console = new TestConsole[IO](inputsRef, outputsRef)
      queue <- Queue.unbounded[IO, ProcessMessage]

      supR <- Supervisor[IO].allocated
      sup = supR._1
      _             <- supR._2 // releaseSup (unused var fix)
      writerFiber   <- new NumberWriter[IO](tmpPath).run(queue).start
      producer      <- TaskProducer.make[IO](queue, sup)(IO.asyncForIO, console)
      producerFiber <- producer.run.start
      _             <- TestUtils.waitForOutputsAtLeast(outputsRef, 1)
      _             <- producerFiber.cancel
      _             <- queue.offer(ProcessMessage.Shutdown)

      prodOutcome <- producerFiber.join
      writOutcome <- writerFiber.join
      _           <- Files[IO].deleteIfExists(tmpPath)
    } yield {
      val producerCancelled = prodOutcome match {
        case Outcome.Canceled() => true
        case _                  => false
      }
      val writerFinished = writOutcome match {
        case Outcome.Succeeded(_) => true
        case Outcome.Canceled()   => true
        case _                    => false
      }
      expect(producerCancelled) and expect(writerFinished)
    }
  }

}
