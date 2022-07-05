package zio

import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.ExecutionContext

trait Fiber[+A]:
  def start(): Unit
  def join: ZIO[A]

class FiberImpl[A](zio: ZIO[A]) extends Fiber[A]:

  sealed trait FiberState
  case class Running(callbacks: List[A => Any]) extends FiberState
  case class Done(result: A) extends FiberState

  val state: AtomicReference[FiberState] = new AtomicReference[FiberState](Running(List.empty))

  def complete(result: A): Unit =
    var loop = true
    while (loop) {
      val oldState = state.get()
      oldState match
        case Running(callbacks) =>
          if state.compareAndSet(oldState, Done(result)) then
            callbacks.foreach(callback => callback(result))
            loop = false
        case Done(result) =>
          throw new Exception("Internal defect: Fiber being completed multiple times")
    }

  def await(callback: A => Any): Unit =
    var loop = true
    while (loop) {
      val oldState = state.get()
      oldState match
        case Running(callbacks) =>
          val newState = Running(callback :: callbacks)
          loop = !state.compareAndSet(oldState, newState)
        case Done(result) =>
          loop = false
          callback(result)
    }

  override def start(): Unit = 
    ExecutionContext.global.execute { () =>
      zio.run(complete)
    }
  override def join: ZIO[A] = 
    ZIO.async(await)

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
        b <- that
        a <- fiberA.join
      } yield (a, b)

    def map[B](f: A => B): ZIO[B] = 
      flatMap(a => ZIO.succeedNow(f(a)))

    def flatMap[B](f: A => ZIO[B]): ZIO[B] =
      ZIO.FlatMap(self, f)

    def run(callback: A => Unit): Unit =

      type Erased = ZIO[Any]
      type ErasedCallback = Any => Any
      type Cont = Any => Erased

      def erase[A](zio: ZIO[A]): Erased =
        zio

      def eraseCallback[A](cb: A => Unit): ErasedCallback =
        cb.asInstanceOf[ErasedCallback]

      val stack = scala.collection.mutable.Stack[Cont]()

      var currentZIO = erase(self)

      var loop = true

      def resume(): Unit =
        loop = true
        run()

      def complete(value: Any): Unit =
        if stack.isEmpty then
          loop = false
          callback(value.asInstanceOf[A])
        else
          val cont = stack.pop()
          currentZIO = cont(value)

      def run(): Unit =
        while (loop) {
          currentZIO match
            case ZIO.Succeed(value) =>
              complete(value)

            case ZIO.Effect(f) =>
              complete(f())

            case ZIO.FlatMap(zio, cont) =>
              stack.push(cont.asInstanceOf[Cont])
              currentZIO = zio

            case ZIO.Async(register) =>
              if stack.isEmpty then
                loop = false
                register(eraseCallback(callback))
              else
                loop = false
                register { a =>
                  currentZIO = ZIO.succeedNow(a)
                  resume()
                }

            case ZIO.Fork(zio) =>
              val fiber = new FiberImpl(zio)
              fiber.start()
              complete(fiber)
        }
      run()

object ZIO:

  def async[A](register: (A => Any) => Any): ZIO[A] = Async(register)

  def succeedNow[A](value: A): ZIO[A] = Succeed(value)

  def succeed[A](value: => A): ZIO[A] = Effect(() => value)

  case class Succeed[A](value: A) extends ZIO[A]
  case class Effect[A](f: () => A) extends ZIO[A]
  case class FlatMap[A, B](zio: ZIO[A], f: A => ZIO[B]) extends ZIO[B]
  case class Async[A](register: (A => Any) => Any) extends ZIO[A]
  case class Fork[A](zio: ZIO[A]) extends ZIO[Fiber[A]]



      

