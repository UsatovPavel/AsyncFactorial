package ru.hse.scala.individual

import cats.Show
import cats.effect.Ref
import cats.effect.std.Console

import java.nio.charset.Charset

class TestConsole[F[_]](
    inputRef: Ref[F, List[String]],
    outputRef: Ref[F, List[String]]
) extends Console[F] {

  override def readLine: F[String] =
    inputRef.modify {
      case head :: tail => (tail, head)
      case Nil          => (Nil, "")
    }

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
