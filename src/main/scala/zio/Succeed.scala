package zio

case class Person(name: String, age: Int)

object Person:
  val peter = Person("Peter", 88)

trait ZIOApp:
  def run: ZIO[Any]
  def main(args: Array[String]): Unit =
    run.run { result =>
      println(s"THE RESULT WAS $result}")
    }


object SucceedNow extends ZIOApp:
  val peterZIO: ZIO[Person] = ZIO.succeedNow(Person.peter)
  override def run: ZIO[Person] = peterZIO