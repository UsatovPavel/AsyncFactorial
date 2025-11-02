package ru.hse.scala.individual

import cats.effect.{ExitCode, IO, IOApp}
//import cats.syntax.all._ // import for syntax

object Task extends IOApp {

  override def run(args: List[String]): IO[ExitCode] =
    IO.println("Hello, world!")
      .as(ExitCode.Success)

}
