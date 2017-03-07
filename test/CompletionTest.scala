
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import com.github.ellbur.akkastreamsignal.Signal
import concurrent.duration._

object CompletionTest extends App {
  {
    implicit val actorSystem = ActorSystem("main")
    implicit val materializer = ActorMaterializer()
    implicit val executionContext = actorSystem.dispatcher
  
    val numbers = Source(0 to 10) flatMapConcat(n => Source(List(n)).delay(0.5.seconds))
  
    val latestNumber = Signal.fromSource(numbers)
  
    ((latestNumber.toSource map (_.toString)) ++ Source(List("end"))).runForeach(println).onComplete { _ =>
      ((latestNumber.toSource map (_.toString)) ++ Source(List("end"))).runForeach(println)
    }
  
    // 0
    // 1
    // 2
    // 3
    // 4
    // 5
    // 6
    // 7
    // 8
    // 9
    // 10
    // end
    // end
  }
}
