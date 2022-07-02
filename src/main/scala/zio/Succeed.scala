package zio

case class Person(name: String, age: Int)

object Person:
  val peter = Person("Peter", 48)

trait ZIOApp:
  def run: ZIO[Any]
  def main(args: Array[String]): Unit =
    run.run { result =>
      println(s"THE RESULT WAS ${result}")
    }


object SucceedNow extends ZIOApp:
  val peterZIO: ZIO[Person] = ZIO.succeedNow(Person.peter)
  override def run: ZIO[Person] = peterZIO


object SucceedNowUhOh extends ZIOApp:
  val howdyZIO = ZIO.succeedNow(println("Howdy!"))
  override def run: ZIO[Any] = ZIO.succeedNow(12)

object Succees extends ZIOApp:
  val howdyZIO = ZIO.succeed(println("Howdy!"))
  override def run: ZIO[Any] = ZIO.succeedNow(23)

object SucceedAgain extends ZIOApp:
  def printLine(message: String): ZIO[Unit] =
    ZIO.succeed(println(message))
  override def run: ZIO[Any] = printLine("Fancy")

object Zip extends ZIOApp:
  val zippedZIO: ZIO[(Int, String)] = ZIO.succeed(8) zip ZIO.succeed("Lo")
  override def run: ZIO[Any] = zippedZIO

object Map extends ZIOApp:
  val zippedZIO = ZIO.succeed(8) zip ZIO.succeed("LO")
  val mappedZIO = zippedZIO.map { case (int, string) =>
    string  * int
  }
  val personZIO = zippedZIO.map { case (age, name) => 
    Person(name, age)
  }
  override def run: ZIO[Any] = personZIO

object MapUhOh extends ZIOApp:
  val zippedZIO = ZIO.succeed(8) zip ZIO.succeed("LO")
  def printLine(message: String): ZIO[Unit] =
    ZIO.succeed(println(message))
  val mappedZIO = zippedZIO.map { tuple =>
    printLine(s"MY BEAUTIFUL TUPLE: $tuple")
  }
  override def run: ZIO[Any] = mappedZIO

object FlatMap extends ZIOApp:
  val zippedZIO = ZIO.succeed(8) zip ZIO.succeed("LO")
  def printLine(message: String): ZIO[Unit] =
    ZIO.succeed(println(message))
  val mappedZIO = zippedZIO.flatMap { tuple =>
    printLine(s"MY BEAUTIFUL TUPLE: $tuple") //.as("Nice")
  }
  override def run: ZIO[Any] = mappedZIO

object ForComprehension extends ZIOApp:
  val zippedZIO = ZIO.succeed(8) zip ZIO.succeed("LO")
  def printLine(message: String): ZIO[Unit] =
    ZIO.succeed(println(message))
  val flatmappedZIO = 
    for {
      tuple <- zippedZIO
      _ <- printLine(s"MY BEAUTIFUL TUPLE: $tuple")
    } yield "Nice"
  override def run: ZIO[Any] = flatmappedZIO

object Async extends ZIOApp:
  val asyncZIO: ZIO[Int] = ZIO.async[Int] { complete =>
    println("ASYNC BEGINNETH!")
    Tread.sleep(2000)
    complete(10)
  }
  override def run: ZIO[Any] = asyncZIO
