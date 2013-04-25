package webhooq.model.dao

import com.hazelcast.nio.DataSerializable
import webhooq.logging.WebhooqLogger
import java.io.{IOException, DataOutput, DataInput}


class ExchangeRef(var vhost:Option[String]=None, var exchange:Option[String]=None) extends DataSerializable with WebhooqLogger {
  def this() {
    this(None,None)
  }
  def writeData(out: DataOutput) {
    if (wqLog.isDebugEnabled) wqLog.debug("writeData started")
    out.writeUTF(this.vhost.getOrElse(throw new IOException("ExchangeRef.vhost may not be None")))
    out.writeUTF(this.exchange.getOrElse(throw new IOException("ExchangeRef.exchange may not be None")))
    if (wqLog.isDebugEnabled) wqLog.debug("writeData finished")
  }

  def readData(in: DataInput) {
    if (wqLog.isDebugEnabled) wqLog.debug("readData started")

    this.vhost = Option(in.readUTF())
    this.vhost.getOrElse(throw new IOException("ExchangeRef.vhost may not be None"))

    this.exchange = Option(in.readUTF())
    this.exchange.getOrElse(throw new IOException("ExchangeRef.exchange may not be None"))

    if (wqLog.isDebugEnabled) wqLog.debug("readData finished")
  }
}
object ExchangeRef {
  def apply(vhost:String, exchange:String):ExchangeRef = new ExchangeRef(Option(vhost), Option(exchange))
}
