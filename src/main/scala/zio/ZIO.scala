package zio

sealed trait ZIO[+A]:
  self =>
    def run(callback: A => Unit): Unit

    def as[B](value: => B): ZIO[B] =
      map(_ => value)

    def zip[B](that: ZIO[B]): ZIO[(A, B)] = 
      for {
        a <- self
        b <- that
      } yield (a, b)

    def map[B](f: A => B): ZIO[B] = 
      flatMap(a => ZIO.succeedNow(f(a)))

    def flatMap[B](f: A => ZIO[B]): ZIO[B] =
      ZIO.FlatMap(self, f)

object ZIO:

  def succeedNow[A](value: A): ZIO[A] = Succeed(value)

  def succeed[A](value: => A): ZIO[A] = Effect(() => value)

  case class Succeed[A](value: A) extends ZIO[A]:
    override def run(callback: A => Unit): Unit =
      callback(value)

  case class Effect[A](f: () => A) extends ZIO[A]:
    override def run(callback: A => Unit): Unit = 
      callback(f())

  case class FlatMap[A, B](zio: ZIO[A], f: A => ZIO[B]) extends ZIO[B]:
    override def run(callback: B => Unit): Unit = 
      zio.run { a =>
        f(a).run(callback)
      }


      

