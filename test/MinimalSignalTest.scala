
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import com.github.ellbur.akkastreamsignal.Signal

import concurrent.duration._

object MinimalSignalTest extends App {
  {
    implicit val actorSystem = ActorSystem("main")
    implicit val materializer = ActorMaterializer()
    implicit val executionContext = actorSystem.dispatcher
    
    val numbers = Source(0 to 10) flatMapConcat(n => Source(List(n)).delay(0.5.seconds))
    
    val latestNumber = Signal.fromSource(numbers)
    
    latestNumber.toSource.runForeach(println)
  }
}
