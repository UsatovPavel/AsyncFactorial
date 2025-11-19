package ru.hse.scala.individual

import cats.effect._
import cats.effect.std.Queue

import scala.concurrent.Promise
import scala.concurrent.duration._

class FileWriter {
  val outputFilename = "out.txt"
  def process(text: String): IO[Unit] = {
  }

  def run: IO[Unit] =
    Queue.unbounded[IO, Promise[Either[ParseError, BigInt]]].flatMap { queue =>

      val consumer =
        queue.take.flatMap(process).foreverM

      val producer =
        IO.println("Enter factorial:") *>
          IO.readLine.flatMap { text =>
            val promise = Promise[Either[ParseError, BigInt]]()
            queue.offer(promise)
            FactorialAccumulator.processInput(text, promise)    // кладём в очередь и ждём следующее значение
          }

      consumer.start *> producer
    }
}
