package webhooq.model.dao

import com.hazelcast.nio.DataSerializable
import webhooq.logging.WebhooqLogger
import java.io.{IOException, DataInput, DataOutput}


class QueueRef (var vhost:Option[String]=None, var queue:Option[String]=None) extends DataSerializable with WebhooqLogger {
  def this() {
    this(None,None)
  }
  def writeData(out: DataOutput) {
    if (wqLog.isDebugEnabled) wqLog.debug("writeData started")
    out.writeUTF(this.vhost.getOrElse(throw new IOException("QueueRef.vhost may not be None")))
    out.writeUTF(this.queue.getOrElse(throw new IOException("QueueRef.queue may not be None")))
    if (wqLog.isDebugEnabled) wqLog.debug("writeData finished")
  }

  def readData(in: DataInput) {
    if (wqLog.isDebugEnabled) wqLog.debug("readData started")

    this.vhost = Option(in.readUTF())
    this.vhost.getOrElse(throw new IOException("QueueRef.vhost may not be None"))

    this.queue = Option(in.readUTF())
    this.queue.getOrElse(throw new IOException("QueueRef.queue may not be None"))

    if (wqLog.isDebugEnabled) wqLog.debug("readData finished")
  }
}
object QueueRef {
  def apply(vhost:String, queue:String):QueueRef = new QueueRef(Option(vhost),Option(queue))
}