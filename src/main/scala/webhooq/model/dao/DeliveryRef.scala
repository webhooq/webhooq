package webhooq.model.dao

import com.hazelcast.nio.DataSerializable
import webhooq.logging.WebhooqLogger
import java.net.{URISyntaxException, URI}
import java.io.{IOException, DataInput, DataOutput}

/**
 */
case class DeliveryRef (var message_id:Option[MessageRef]=None, var uri:Option[URI]=None) extends DataSerializable with WebhooqLogger {
  def this() {
    this(None,None)
  }

  def writeData(out: DataOutput):Unit = {
    if (wqLog.isDebugEnabled) wqLog.debug("writeData started")
    this.message_id.getOrElse(throw new IOException("DeliveryRef.message_id may not be None")).writeData(out)
    out.writeUTF(uri.getOrElse(throw new IOException("DeliveryRef.uri may not be None")).toString)
    if (wqLog.isDebugEnabled) wqLog.debug("writeData finished")
  }

  def readData(in: DataInput): Unit = {
    if (wqLog.isDebugEnabled) wqLog.debug("readData started")

    val message_id = new MessageRef()
    message_id.readData(in)
    this.message_id = Option(message_id)

    try { this.uri = Option(new URI(in.readUTF()))}
    catch { case e:URISyntaxException => throw new IOException("while attempting to parse DeliveryRef.uri", e)}

    if (wqLog.isDebugEnabled) wqLog.debug("readData finished")
  }
}

object DeliveryRef {
  def apply(message_id:MessageRef, uri:URI):DeliveryRef = new DeliveryRef(Option(message_id), Option(uri))
}