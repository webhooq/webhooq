package webhooq.event

import akka.actor._
import webhooq.logging.WebhooqLogger
import java.util.concurrent.{BlockingQueue, TimeUnit}
import scala.Some


class HazelcastQueueLister[T] (val queue:BlockingQueue[T], val actorConstructionCallback: ActorContext => ActorRef) extends Actor with WebhooqLogger {
  import HazelcastQueueLister._

  var shutdown = false
  var running  = false

  def receive = {
    case Shutdown =>
      if (wqLog.isInfoEnabled) wqLog.info("Shutdown message received.")
      this.shutdown = true
    case Listen =>
      if (!running) {
        running = true
//        val actor = actorConstructionCallback(context)
        if (wqLog.isDebugEnabled) wqLog.debug("entering incoming queue listen loop")
        while(!this.shutdown) {
          if (wqLog.isDebugEnabled) wqLog.debug("listening...")
          Option(queue.poll(1,TimeUnit.SECONDS)) match {
            case None =>
              if (wqLog.isDebugEnabled) wqLog.debug("incoming queue was empty, retrying")
            case Some(incoming) =>
              if (wqLog.isInfoEnabled) wqLog.info("dispatching incoming message '%s' to callback actor".format(incoming.toString))
              actorConstructionCallback(context) ! QueueMessage(incoming)
          }
        }
        if (wqLog.isDebugEnabled) wqLog.debug("exiting incoming queue listen loop")
      }
  }
}

object HazelcastQueueLister {
  abstract class HazelcastQueueListerMessage
  case object Listen extends HazelcastQueueListerMessage
  case object Shutdown extends HazelcastQueueListerMessage
  case class  QueueMessage[T](message:T)

  def start[T] (name:String, system:ActorSystem, queue:BlockingQueue[T],actorConstructionCallback:ActorContext => ActorRef):ActorRef = {
    system.actorOf(Props(new HazelcastQueueLister[T](queue,actorConstructionCallback)), name = name)
  }
}