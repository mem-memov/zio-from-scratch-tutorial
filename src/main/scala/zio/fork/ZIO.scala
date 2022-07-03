package zio.fork

import scala.concurrent.ExecutionContext

trait ZIO[+A] { self => 
  def run(callback: A => Unit): Unit
  def flatMap[B](f: A => ZIO[B]): ZIO[B] =
    ZIO.FlatMap(self, f)
  def map[B](f: A => B): ZIO[B] =
    flatMap { a =>
      ZIO.succeed(f(a))
    }
  def fork(zio: ZIO[A]): ZIO[Fiber[A]] = ZIO.Fork(self)
  }

trait Fiber[+A]:
  def start(): Unit
  def join: ZIO[A]

class FiberImpl[A](zio: ZIO[A]) extends Fiber[A]:
  var optionalResult: Option[A] = None
  var callbacks = List.empty[A => Any]
  override def start(): Unit = 
    ExecutionContext.global.execute { () =>
      zio.run { a =>
        optionalResult = Some(a)
        callbacks.foreach { callback => 
          callback(a)
        }
      }
    }
  override def join: ZIO[A] = {
    optionalResult match
      case Some(a) =>
        ZIO.succeedNow(a)
      case None =>
        ZIO.async { complete =>
          callbacks = complete :: callbacks
        }
  }



object ZIO:

  def succeedNow[A](value: A): ZIO[A] =
    new ZIO[A]:
      override def run(callback: A => Unit): Unit = 
        callback(value)

  def succeed[A](value: A): ZIO[A] = Effect(value)

  def async[A](register: (A => Any) => Any): ZIO[A] = Async(register)

  case class Async[A](register: (A => Any) => Any) extends ZIO[A]:
    override def run(callback: A => Unit): Unit = 
      register(callback)

  case class Effect[A](value: A) extends ZIO[A]:
    override def run(callback: A => Unit): Unit = 
      callback(value)

  case class FlatMap[A, B](zio: ZIO[A], f: A => ZIO[B]) extends ZIO[B]:
    override def run(callback: B => Unit): Unit = 
      zio.run { a =>
        f(a).run(callback)
      }

  case class Fork[A](zio: ZIO[A]) extends ZIO[Fiber[A]]:
    override def run(callback: Any => Unit): Unit = 
      val fiber = new FiberImpl(zio)
      fiber.start()
      callback(fiber)