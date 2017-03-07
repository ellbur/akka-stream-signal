
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import org.reactivestreams.{Publisher, Subscriber, Subscription}

import scala.collection.mutable
import scala.concurrent.duration._

object SignalTest1 extends App {
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
    
    val lock = new Object
    var current: Option[String] = None
    case class OurSubscription(subscriber: Subscriber[_ >: String], var credit: Long)
    val subscribers = mutable.Map[Subscription, OurSubscription]()
    
    delayedLines.runWith(Sink.asPublisher(fanout = false)).subscribe(new Subscriber[String] {
      private[this] var subscription: Option[Subscription] = None
      
      override def onError(t: Throwable): Unit = {
        t.printStackTrace()
      }
      
      override def onSubscribe(s: Subscription): Unit = {
        subscription = Some(s)
        subscription foreach (_.request(1))
      }
      
      override def onComplete() { }
      
      override def onNext(next: String): Unit = {
        val toNotify =
          lock.synchronized {
            current = Some(next)
            val toNotify = (subscribers.values map (s => (s.subscriber, s.credit))).toSeq
            subscribers.values foreach (s => s.credit = math.max(s.credit-1, 0))
            toNotify
          }
        
        toNotify foreach { case (subscriber, credit) =>
          if (credit > 0) {
            subscriber.onNext(next)
          }
        }
        
        subscription foreach (_.request {
          if (toNotify.isEmpty)
            1
          else
            (toNotify map (_._2)).min
        })
      }
    })
    
    object signalPublisher extends Publisher[String] {
      override def subscribe(subscriber: Subscriber[_ >: String]): Unit = {
        subscriber.onSubscribe(new Subscription {
          {
            val currentToSend =
              lock.synchronized {
                subscribers += ((this, OurSubscription(subscriber, 0)))
                current
              }
            
            currentToSend foreach (subscriber.onNext(_))
          }
          
          override def cancel(): Unit = {
            lock.synchronized {
              subscribers.remove(this)
            }
          }
          
          override def request(n: Long): Unit = {
            lock.synchronized {
              val ourSubscription = subscribers(this)
              ourSubscription.credit += n
            }
          }
        })
      }
    }
    
    val signalStream = Source.fromPublisher(signalPublisher)
    
    signalStream.runForeach(println)
  }
}
