package webhooq.model.dao

import com.hazelcast.nio.DataSerializable
import webhooq.logging.WebhooqLogger
import java.io.{IOException, DataInput, DataOutput}

class Argument(var key:Option[String]=None, var value:Option[String]=None) extends DataSerializable with WebhooqLogger {
  def this() {
    this(None,None)
  }

  def writeData(out: DataOutput) {
    if (wqLog.isDebugEnabled) wqLog.debug("writeData started")
    out.writeUTF(this.key.getOrElse(throw new IOException("Argument.key may not be None")))
    out.writeUTF(this.value.getOrElse(throw new IOException("Argument.value may not be None")))
    if (wqLog.isDebugEnabled) wqLog.debug("writeData finished")
  }

  def readData(in: DataInput) {
    if (wqLog.isDebugEnabled) wqLog.debug("readData started")

    this.key   = Option(in.readUTF())
    this.key.getOrElse(throw new IOException("Argument.key may not be None"))

    this.value = Option(in.readUTF())
    this.value.getOrElse(throw new IOException("Argument.value may not be None"))

    if (wqLog.isDebugEnabled) wqLog.debug("readData finished")
  }
}
object Argument {
  def apply(key:String, value:String):Argument = new Argument(Option(key), Option(value))
}