package webhooq

import webhooq.event.HazelcastQueueLister
import webhooq.logging.WebhooqLogger

object Main extends WebhooqLogger with Akka with Hazelcast with Netty {
  def actorSystemName: String = "webhooq"

  wqLog.info("Configuring Outgoing Queue Listener")
  val outgoing = HazelcastQueueLister.start("OutgoingQueueListener", akkaSystem, Schema.outgoing, Dispatcher.createActor("OutgoingRouter-%s") )


  wqLog.info("Configuring Incoming Queue Listener")
  val incoming = HazelcastQueueLister.start("IncomingQueueListener", akkaSystem, Schema.incoming, Router.createActor("IncomingRouter-%s") )


  def main (args: Array[String]) = {
    outgoing ! HazelcastQueueLister.Listen
    wqLog.info("Outgoing Queue Listener initialized")

    incoming ! HazelcastQueueLister.Listen
    wqLog.info("Incoming Queue Listener initialized")

  }

  Runtime.getRuntime.addShutdownHook(new Thread("Webhooq Shutdown Thread") {
    override def run() : Unit = {

      wqLog.info("Shutting down Incoming Queue Listener")
      incoming ! HazelcastQueueLister.Shutdown

      wqLog.info("Shutting down Outoing Queue Listener")
      outgoing ! HazelcastQueueLister.Shutdown

    }
  })

}
