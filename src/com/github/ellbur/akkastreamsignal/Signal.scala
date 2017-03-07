
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
  private var subscription: Option[Subscription] = None
  
  publisher.subscribe(new Subscriber[T] {
    override def onError(t: Throwable): Unit = {
      t.printStackTrace()
    }
    
    override def onSubscribe(s: Subscription): Unit = {
      lock.synchronized(subscription = Some(s))
      lock.synchronized(subscription) foreach (_.request(1))
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
      val (anySubscribers, toNotify) =
        lock.synchronized {
          current = Some(next)
          
          val toNotify =
            subscribers.values.toSeq flatMap { s =>
              if (s.credit > 0) {
                s.credit -= 1
                Seq(s.subscriber): Seq[Subscriber[_ >: T]]
              }
              else
                Seq()
            }
          
          val anySubscribers = subscribers.nonEmpty
          
          (anySubscribers, toNotify)
        }
      
      toNotify foreach (_.onNext(next))
      
      if (!anySubscribers)
        lock.synchronized(subscription) foreach (_.request(1))
    }
  })
  
  private object signalPublisher extends Publisher[T] {
    override def subscribe(subscriber: Subscriber[_ >: T]): Unit = {
      subscriber.onSubscribe(new Subscription {
        private val ourSubscription = OurSubscription(subscriber, 0)
        
        {
          val (completed, currentToSend) =
            lock.synchronized {
              subscribers += ((this, ourSubscription))
              (signal.completed, current)
            }
  
          currentToSend foreach (subscriber.onNext(_))
          
          if (completed)
            subscriber.onComplete()
        }
        
        override def cancel(): Unit = {
          val nowEmpty =
            lock.synchronized {
              subscribers.remove(this)
              subscribers.isEmpty
            }
          
          if (nowEmpty) {
            lock.synchronized(subscription) foreach (_.request(1))
          }
        }
        
        override def request(n: Long): Unit = {
          val toRequest =
            lock.synchronized {
              val oldMin =
                if (subscribers.nonEmpty)
                  (subscribers.values map (_.credit)).min
                else
                  0
              
              ourSubscription.credit += n
              
              val newMin =
                if (subscribers.nonEmpty)
                  (subscribers.values map (_.credit)).min
                else
                  0
              
              newMin - oldMin
            }
  
          if (toRequest > 0)
            lock.synchronized(subscription) foreach (_.request(n))
        }
      })
    }
  }
  
  def toSource: Source[T, NotUsed] = Source.fromPublisher(signalPublisher)
}

object Signal {
  def fromSource[T](source: Source[T, NotUsed])(implicit materializer: Materializer): Signal[T] = new Signal(source.runWith(Sink.asPublisher(fanout = false)))
}
