package ru.hse.scala.individual.semaphore

import cats.Show
import cats.effect.{Async, Ref}
import cats.effect.std.{Console, Queue}
import cats.syntax.all._

import java.nio.charset.Charset

final class TestConsole[F[_]] private (
    inQ: Queue[F, String],
    outputRef: Ref[F, List[String]]
) extends Console[F] {

  override def readLine: F[String] =
    inQ.take

  override def readLineWithCharset(charset: Charset): F[String] =
    readLine

  override def print[A](a: A)(implicit S: Show[A]): F[Unit] =
    outputRef.update(_ :+ S.show(a))

  override def println[A](a: A)(implicit S: Show[A]): F[Unit] =
    outputRef.update(_ :+ (S.show(a) + "\n"))

  override def error[A](a: A)(implicit S: Show[A]): F[Unit] =
    outputRef.update(_ :+ s"ERROR: ${S.show(a)}")

  override def errorln[A](a: A)(implicit S: Show[A]): F[Unit] =
    outputRef.update(_ :+ s"ERROR: ${S.show(a)}\n")
}

object TestConsole {

  def create[F[_]: Async](initialInputs: List[String], outputsRef: Ref[F, List[String]]): F[TestConsole[F]] =
    for {
      q <- Queue.unbounded[F, String]
      _ <- initialInputs.traverse_(q.offer)
    } yield new TestConsole[F](q, outputsRef)

  def fromRef[F[_]: Async](inputRef: Ref[F, List[String]], outputsRef: Ref[F, List[String]]): F[TestConsole[F]] =
    for {
      q   <- Queue.unbounded[F, String]
      ins <- inputRef.get
      _   <- ins.traverse_(q.offer)
    } yield new TestConsole[F](q, outputsRef)

  def withQueue[F[_]: Async](
      initial: List[String],
      outputsRef: Ref[F, List[String]]
  ): F[(TestConsole[F], Queue[F, String])] =
    for {
      q <- Queue.unbounded[F, String]
      _ <- initial.traverse_(q.offer)
    } yield (new TestConsole[F](q, outputsRef), q)
}
