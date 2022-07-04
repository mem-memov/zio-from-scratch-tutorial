package zio

import scala.concurrent.ExecutionContext

trait Fiber[+A]:
  def start(): Unit
  def join: ZIO[A]

class FiberImpl[A](zio: ZIO[A]) extends Fiber[A]:
  var maybeResult: Option[A] = None
  var callbacks = List.empty[A => Any]
  override def start(): Unit = 
    ExecutionContext.global.execute { () =>
      zio.run { a =>
        maybeResult = Some(a)
        callbacks.foreach { callback =>
          callback(a)
        }
      }
    }
  override def join: ZIO[A] = 
    maybeResult match
      case Some(a) =>
        ZIO.succeedNow(a)
      case None =>
        ZIO.async { complete =>
          callbacks = complete :: callbacks
        }


sealed trait ZIO[+A]:
  self =>

    def repeat(n: Int): ZIO[Unit] =
      if n <= 0 then
        ZIO.succeedNow(())
      else
        self *> repeat(n - 1)

    def fork: ZIO[Fiber[A]] = 
      ZIO.Fork(self)

    def as[B](value: => B): ZIO[B] =
      map(_ => value)

    def zip[B](that: ZIO[B]): ZIO[(A, B)] =
      zipWith(that)((a, b) => (a, b))

    def zipWith[B, C](that: => ZIO[B])(f: (A, B) => C): ZIO[C] =
      for {
        a <- self
        b <- that
      } yield f(a, b)

    def zipRight[B](that: => ZIO[B]): ZIO[B] =
      zipWith(that)((_, b) => b)

    def *>[B](that: => ZIO[B]): ZIO[B] = self zipRight that

    def zipPar[B](that: ZIO[B]): ZIO[(A, B)] = 
      for {
        fiberA <- self.fork
        fiberB <- that.fork
        a <- fiberA.join
        b <- fiberB.join
      } yield (a, b)

    def map[B](f: A => B): ZIO[B] = 
      flatMap(a => ZIO.succeedNow(f(a)))

    def flatMap[B](f: A => ZIO[B]): ZIO[B] =
      ZIO.FlatMap(self, f)

    def run(callback: A => Unit): Unit =

      type Erased = ZIO[Any]
      type Cont = Any => Erased

      def erase[A](zio: ZIO[A]): Erased =
        zio
        
      def eraseCallback[A, B](cb: A => ZIO[B]): Cont =
        cb

      val stack = scala.collection.mutable.Stack[Cont]()

      var currentZIO = erase(self)

      var loop = true

      def complete(value: Any): Unit =
        if stack.isEmpty then
          loop = false
          callback(value.asInstanceOf[A])
        else
          val cont = stack.pop()
          currentZIO = cont(value)

      while (loop) {
        currentZIO match
          case ZIO.Succeed(value) =>
            complete(value)

          case ZIO.Effect(f) =>
            complete(f())

          case ZIO.FlatMap(zio, cont) =>
            stack.push(cont)
            currentZIO = zio

          case ZIO.Async(register) =>
            ???
//            register(callback)
          case ZIO.Fork(zio) =>
            ???
//            val fiber = new FiberImpl(zio)
//            fiber.start()
//            callback(fiber)
      }

object ZIO:

  def async[A](register: (A => Any) => Any): ZIO[A] = Async(register)

  def succeedNow[A](value: A): ZIO[A] = Succeed(value)

  def succeed[A](value: => A): ZIO[A] = Effect(() => value)

  case class Succeed[A](value: A) extends ZIO[A]
  case class Effect[A](f: () => A) extends ZIO[A]
  case class FlatMap[A, B](zio: ZIO[A], f: A => ZIO[B]) extends ZIO[B]
  case class Async[A](register: (A => Any) => Any) extends ZIO[A]
  case class Fork[A](zio: ZIO[A]) extends ZIO[Fiber[A]]



      

