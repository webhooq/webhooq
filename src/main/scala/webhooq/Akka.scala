package webhooq

import akka.actor.ActorSystem


trait Akka {
  def actorSystemName:String
  val akkaSystem = ActorSystem(actorSystemName)

  Runtime.getRuntime().addShutdownHook(new Thread("Webhooq Akka Shutdown Thread") {
    override def run() : Unit = { akkaSystem.shutdown() }
  })
}
