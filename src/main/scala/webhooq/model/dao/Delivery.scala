package webhooq.model.dao

import com.hazelcast.nio.DataSerializable
import webhooq.logging.WebhooqLogger
import java.io.{DataInput, IOException, DataOutput}


class Delivery (var delivery_time:Option[Long], var delivery_response_status:Option[Int]) extends DataSerializable with WebhooqLogger {
  def this() {
    this(None,None)
  }
  def writeData(out: DataOutput) {
    if (wqLog.isDebugEnabled) wqLog.debug("writeData started")
    out.writeLong(this.delivery_time.getOrElse(throw new IOException("Delivery.delivery_time may not be None")))
    out.writeInt(this.delivery_response_status.getOrElse(throw new IOException("Delivery.delivery_response_status may not be None")))
    if (wqLog.isDebugEnabled) wqLog.debug("writeData finished")
  }

  def readData(in: DataInput) {
    if (wqLog.isDebugEnabled) wqLog.debug("readData started")

    this.delivery_time = Option(in.readLong())
    this.delivery_time.getOrElse(throw new IOException("Delivery.delivery_time may not be None"))

    this.delivery_response_status = Option(in.readInt())
    this.delivery_response_status.getOrElse(throw new IOException("Delivery.delivery_response_status may not be None"))

    if (wqLog.isDebugEnabled) wqLog.debug("readData finished")
  }


}

object Delivery {
  def apply(delivery_time:Long, delivery_response_status:Int):Delivery = new Delivery(Option(delivery_time), Option(delivery_response_status))
}