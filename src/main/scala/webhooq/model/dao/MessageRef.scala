package webhooq.model.dao

import java.util.UUID
import com.hazelcast.nio.DataSerializable
import webhooq.logging.WebhooqLogger
import java.io.{IOException, DataInput, DataOutput}


class MessageRef (var vhost:Option[String]=None, var message_id:Option[UUID]=None) extends DataSerializable with WebhooqLogger {
  def this() {
    this(None,None)
  }
  def writeData(out: DataOutput) {
    if (wqLog.isDebugEnabled) wqLog.debug("writeData started")
    val id = this.message_id.getOrElse(throw new IOException("MessageRef.message_id may not be None"))
    out.writeUTF(this.vhost.getOrElse(throw new IOException("MessageRef.vhost may not be None")))
    out.writeLong(id.getMostSignificantBits)
    out.writeLong(id.getLeastSignificantBits)
    if (wqLog.isDebugEnabled) wqLog.debug("writeData finished")
  }

  def readData(in: DataInput) {
    if (wqLog.isDebugEnabled) wqLog.debug("readData started")

    this.vhost = Option(in.readUTF())
    this.vhost.getOrElse(throw new IOException("MessageRef.vhost may not be None"))

    this.message_id = Option(new UUID(in.readLong(),in.readLong()))
    this.message_id.getOrElse(throw new IOException("MessageRef.message_id may not be None"))

    if (wqLog.isDebugEnabled) wqLog.debug("readData finished")
  }
}
object MessageRef {
  def apply(vhost:String, message_id:UUID):MessageRef = new MessageRef(Option(vhost),Option(message_id))
}