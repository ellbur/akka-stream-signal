
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import com.github.ellbur.akkastreamsignal.Signal

import concurrent.duration._

object SignalTest2 extends App {
  {
    implicit val actorSystem = ActorSystem("main")
    implicit val materializer = ActorMaterializer()
    implicit val executionContext = actorSystem.dispatcher
    
    val numbers = 0 to 10
    val progressLines = numbers map (n => f"progress ${n/10.0}%.2f")
    
    val lines = Source(
      progressLines ++ List("result images/foo.png")
    )
    
    val delayedLines = lines.flatMapConcat { line =>
      Source(List(line)).delay(0.5.second)
    }
    
    val latestLine = Signal.fromSource(delayedLines)
    
    latestLine.toSource.runForeach(println)
  }
}
