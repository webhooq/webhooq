package webhooq.model.dao
import webhooq.model.Link
import com.hazelcast.nio.DataSerializable
import webhooq.logging.WebhooqLogger
import java.io.{IOException, DataOutput, DataInput}

class Outgoing (var message_id:MessageRef, var queueRef:QueueRef, var headers:Map[String,String], var link:Link) extends DataSerializable with WebhooqLogger {
  def this() {
    this(null,null,null,null)
  }
  def writeData(out: DataOutput) {
    this.message_id.writeData(out)
    this.queueRef.writeData(out)
    out.writeInt(this.headers.size)
    this.headers.foreach { t =>
      out.writeUTF(t._1)
      out.writeUTF(t._2)
    }
    out.writeUTF(link.toString)
  }

  def readData(in: DataInput) {
    this.message_id = new MessageRef()
    this.message_id.readData(in)

    this.queueRef = new QueueRef()
    this.queueRef.readData(in)

    this.headers = readProperties(in,in.readInt(), scala.collection.mutable.Map.empty[String,String]).toMap

    this.link = Link.parse(in.readUTF()).getOrElse(throw new IOException("could not parse link for Queue '%s'".format(this.queueRef.queue.getOrElse("<queue name unavailable>"))))

  }

  def readProperties(in: DataInput, remaining:Int, acc:scala.collection.mutable.Map[String,String]):scala.collection.mutable.Map[String,String] = {
    if (remaining == 0) acc
    else readProperties(in, remaining - 1, acc + (in.readUTF() -> in.readUTF()))
  }
}
