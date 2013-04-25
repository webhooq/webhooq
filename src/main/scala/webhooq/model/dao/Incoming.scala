package webhooq.model.dao

import java.util.UUID
import com.hazelcast.nio.DataSerializable
import java.io.{DataOutput, DataInput}
import webhooq.model._
import webhooq.logging.WebhooqLogger

/**
 * Models an incoming message from Netty, suitable for serialization within Hazelcast
 */
class Incoming(var exchange:ExchangeRef, var routing_key:String, var headers:Map[String,String], var message_id:MessageRef) extends DataSerializable with WebhooqLogger {
  def this() {
    this(null,null,null,null)  // ASS!
  }

  def writeData(out: DataOutput):Unit = {
    if (wqLog.isDebugEnabled) wqLog.debug("writeData started")
    this.exchange.writeData(out)
    out.writeUTF(this.routing_key)
    message_id.writeData(out)
    out.writeInt(this.headers.size)
    this.headers.foreach { t =>
      out.writeUTF(t._1)
      out.writeUTF(t._2)
    }
    if (wqLog.isDebugEnabled) wqLog.debug("writeData finished")
  }

  def readData(in: DataInput): Unit = {
    if (wqLog.isDebugEnabled) wqLog.debug("readData started")

    this.exchange = new ExchangeRef()
    this.exchange.readData(in)

    this.routing_key = in.readUTF()

    this.message_id = new MessageRef()
    this.message_id.readData(in)

    this.headers = readProperties(in,in.readInt(), scala.collection.mutable.Map.empty[String,String]).toMap
    if (wqLog.isDebugEnabled) wqLog.debug("readData finished")
  }

  def readProperties(in: DataInput, remaining:Int, acc:scala.collection.mutable.Map[String,String]):scala.collection.mutable.Map[String,String] = {
    if (remaining == 0) acc
    else readProperties(in, remaining - 1, acc + (in.readUTF() -> in.readUTF()))
  }

  override def toString(): String = {
    val sb = new StringBuilder()

    sb.append("exchange").append("=").append(this.exchange + "").append(";")
    sb.append("routing_key").append("=").append(this.routing_key + "").append(";")
    headers.foldLeft((sb.append("headers").append("={"),false)) {(sb,hdr) =>
      if (sb._2) sb._1.append(",")
      (sb._1.append(hdr._1).append("=").append(hdr._2), true)
    }._1.append("};")
    sb.toString()
  }
  def appendValue (sb:StringBuilder, key:String, value:String, sep:Option[String]): StringBuilder = {
    (if (sep.isDefined) sb.append(sep.get) else sb).append(key).append("=").append(value)
  }
}
