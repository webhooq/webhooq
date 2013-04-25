package webhooq.logging

import org.apache.log4j.{LogManager, BasicConfigurator, Level, Logger}


object WebhooqLogger {
  val level = parseLogLevel(Option(System.getProperty("wq.logging.level")),Level.INFO)
  System.err.println("Configuring Logging to log at "+level.toString+" level.")
  BasicConfigurator.configure()
  LogManager.getRootLogger().setLevel(level)
  Logger.getLogger(this.getClass.getName).info("logging initialized.")

  /**
   * Helper funciton to set the logging level from the commandline
   */
  def parseLogLevel(s:Option[String], default:Level=Level.DEBUG):Level = {
    s match {
      case Some("ALL"      ) => Level.ALL
      case Some("DEBUG"    ) => Level.DEBUG
      case Some("ERROR"    ) => Level.ERROR
      case Some("FATAL"    ) => Level.FATAL
      case Some("INFO"     ) => Level.INFO
      case Some("OFF"      ) => Level.OFF
      case Some("TRACE"    ) => Level.TRACE
      case Some("WARN"     ) => Level.WARN
      case _                 => default
    }
  }
  def getLogger(name:String):Logger = Logger.getLogger(name)
}
trait WebhooqLogger {
  lazy val wqLog = WebhooqLogger.getLogger(this.getClass.getName)
}
