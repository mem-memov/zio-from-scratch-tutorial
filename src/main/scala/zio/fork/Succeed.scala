package zio.fork

trait ZIOApp:
  def z: ZIO[Any]
  def main(args: Array[String]): Unit =
    z.run { result =>
      println(s"THE RESULT IS: $result")
    }
    Thread.sleep(3000)
  
object AsyncExample extends ZIOApp:
  val asyncZIO = ZIO.async[String] { complete => 
    println("STARTING ASYNC OPERATION...")
    Thread.sleep(2000)
    println("ASYNC OPERATION FINISHED!")
    complete("Nothing found")
  }

  override def z: ZIO[Any] = asyncZIO

object ForkExample extends ZIOApp:
  val asyncZIO = ZIO.async[Int] { complete => 
    println("STARTING ASYNC OPERATION...")
    Thread.sleep(2000)
    println("ASYNC OPERATION FINISHED!")
    complete(scala.util.Random.nextInt(999))
  }

  def printLine(message: String): ZIO[Unit] =
    ZIO.succeed(println(message))

  val forkedZIO = for {
    fiber_1 <- asyncZIO.fork
    fiber_2 <- asyncZIO.fork
    _ <- printLine("NICE!")
    int_1 <- fiber_1.join
    int_2 <- fiber_2.join
  } yield s"MY BEAUTIFUL INTS: $int_1, $int_2"

  override def z: ZIO[Any] = forkedZIO