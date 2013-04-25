package webhooq

import com.hazelcast.config.Config
import com.hazelcast.core.{Hazelcast => Hz}
import webhooq.logging.WebhooqLogger

trait Hazelcast extends WebhooqLogger {

  val hazelcast = Hazelcast.hazelcast
}

object  Hazelcast extends WebhooqLogger {
  wqLog.info("Configuring Hazelcast.")
  val hazelcastConfig = new Config()
  wqLog.info("Telling Hazelcast to use Log4j for it's logging.")
  hazelcastConfig.setProperty("hazelcast.logging.type", "log4j")
  val hazelcast = Hz.newHazelcastInstance(hazelcastConfig)
  wqLog.info("Hazelcast initialized.")

  Runtime.getRuntime().addShutdownHook(new Thread("Webhooq Hazelcast Shutdown Thread") {
    override def run() : Unit = {
      wqLog.info("Shutting down Hazelcast")
      hazelcast.getLifecycleService.shutdown()
    }
  })
}
