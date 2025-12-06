package ru.hse.scala.individual

import cats.effect._
import cats.effect.std.{Queue, Supervisor}
import fs2.io.file.Files
import weaver.SimpleIOSuite

object TaskProducerSpec extends SimpleIOSuite {
  test("producer abrupt cancel then writer finishes after Shutdown") { // очень сложный, нужно проще
    for {
      tmpPath    <- Files[IO].createTempFile(None, "cancel-", ".txt", None)
      inputsRef  <- Ref.of[IO, List[String]](TestUtils.largeInputData.values.map(_.toString))
      outputsRef <- Ref.of[IO, List[String]](List.empty)
      console = new TestConsole[IO](inputsRef, outputsRef)
      queue <- Queue.unbounded[IO, ProcessMessage]
      supR  <- Supervisor[IO].allocated
      sup        = supR._1
      releaseSup = supR._2
      writerFiber   <- new NumberWriter[IO](tmpPath).run(queue).start
      producerFiber <- new TaskProducer[IO](queue, sup)(IO.asyncForIO, console).run.start
      _             <- TestUtils.waitForOutputsAtLeast(outputsRef, 1)
      _             <- producerFiber.cancel
      _             <- queue.offer(ProcessMessage.Shutdown)
      prodOutcome   <- producerFiber.join
      writOutcome   <- writerFiber.join
      _             <- releaseSup
      _             <- Files[IO].deleteIfExists(tmpPath)
    } yield {
      val producerCancelled = prodOutcome match {
        case cats.effect.Outcome.Canceled() => true
        case _                              => false
      }
      val writerFinished = writOutcome match {
        case cats.effect.Outcome.Succeeded(_) => true
        case cats.effect.Outcome.Canceled()   => true
        case _                                => false
      }
      expect(producerCancelled) and expect(writerFinished)
    }
  }

}
