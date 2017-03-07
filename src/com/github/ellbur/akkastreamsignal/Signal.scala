
package com.github.ellbur.akkastreamsignal

import akka.NotUsed
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import org.reactivestreams.{Publisher, Subscriber, Subscription}

import scala.collection.mutable

class Signal[T](publisher: Publisher[T]) { signal =>
  private val lock = new Object
  private var current: Option[T] = None
  private var completed: Boolean = false
  private case class OurSubscription(subscriber: Subscriber[_ >: T], var credit: Long)
  private val subscribers = mutable.Map[Subscription, OurSubscription]()
  
  publisher.subscribe(new Subscriber[T] {
    private[this] var subscription: Option[Subscription] = None
    
    override def onError(t: Throwable): Unit = {
      t.printStackTrace()
    }
    
    override def onSubscribe(s: Subscription): Unit = {
      subscription = Some(s)
      subscription foreach (_.request(1))
    }
    
    override def onComplete(): Unit = {
      val toNotify =
        lock.synchronized {
          completed = true
          val toNotify = (subscribers.values map (s => s.subscriber)).toSeq
          subscribers.clear()
          toNotify
        }
      
      toNotify foreach (_.onComplete())
    }
    
    override def onNext(next: T): Unit = {
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
  
  private object signalPublisher extends Publisher[T] {
    override def subscribe(subscriber: Subscriber[_ >: T]): Unit = {
      subscriber.onSubscribe(new Subscription {
        {
          val (completed, currentToSend) =
            lock.synchronized {
              subscribers += ((this, OurSubscription(subscriber, 0)))
              (signal.completed, current)
            }
          
          if (completed)
            subscriber.onComplete()
          else
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
  
  def toSource: Source[T, NotUsed] = Source.fromPublisher(signalPublisher)
}

object Signal {
  def fromSource[T](source: Source[T, NotUsed])(implicit materializer: Materializer): Signal[T] = new Signal(source.runWith(Sink.asPublisher(fanout = false)))
}
