package webhooq.model.dao

import com.hazelcast.nio.DataSerializable
import webhooq.logging.WebhooqLogger
import java.io.{IOException, DataInput, DataOutput}
import webhooq.model.{LinkValueParser, Link}


class Binding(var binding:Either[ExchangeBinding,QueueBinding]) extends DataSerializable with WebhooqLogger {
  def this() {
    this(null)  // ASS!
  }

  def isExchange = this.binding.isLeft
  def isQueue = this.binding.isRight
  def getRoutingKey(): String = binding match {
    case Left(exchangee) => exchangee.routing_key
    case Right(queue) => queue.routing_key
  }

  def writeData(out: DataOutput) {
    if (wqLog.isDebugEnabled) wqLog.debug("writeData started")
    binding match {
      case Left(exchange) =>
        out.writeByte(0)
        exchange.writeData(out)
      case Right(queue) =>
        out.writeByte(1)
        queue.writeData(out)
    }
    if (wqLog.isDebugEnabled) wqLog.debug("writeData finished")
  }

  def readData(in: DataInput) {
    if (wqLog.isDebugEnabled) wqLog.debug("readData started")
    in.readByte().toInt match {
      case 0 =>
        val exchangeBinding = new ExchangeBinding()
        exchangeBinding.readData(in)
        this.binding = Left(exchangeBinding)
      case 1 =>
        val queueBinding = new QueueBinding()
        queueBinding.readData(in)
        this.binding = Right(queueBinding)
    }
    if (wqLog.isDebugEnabled) wqLog.debug("readData finished")
  }
}

class QueueBinding(var queueRef:QueueRef, var routing_key:String, var link:Link) extends DataSerializable with WebhooqLogger {
  def this() {
    this(null,null,null)  // ASS!
  }

  def writeData(out: DataOutput) {
    if (wqLog.isDebugEnabled) wqLog.debug("writeData started")
    queueRef.writeData(out)
    out.writeUTF(this.routing_key)
    out.writeUTF(this.link.toString())
    if (wqLog.isDebugEnabled) wqLog.debug("writeData finished")
  }

  def readData(in: DataInput) {
    if (wqLog.isDebugEnabled) wqLog.debug("readData started")

    this.queueRef = new QueueRef()
    this.queueRef.readData(in)

    this.routing_key = in.readUTF()

    this.link = Link.parse(in.readUTF()).getOrElse(throw new IOException("could not parse link for Queue '%s' with routing-key '%s'.".format(this.queueRef.queue.getOrElse("<queue name unavailable>"), this.routing_key)))

    if (wqLog.isDebugEnabled) wqLog.debug("readData finished")
  }
}

class ExchangeBinding(var exchangeRef:ExchangeRef, var routing_key:String) extends DataSerializable with WebhooqLogger {
  def this() {
    this(null,null)  // ASS!
  }

  def writeData(out: DataOutput) {
    if (wqLog.isDebugEnabled) wqLog.debug("writeData started")
    exchangeRef.writeData(out)
    out.writeUTF(this.routing_key)
    if (wqLog.isDebugEnabled) wqLog.debug("writeData finished")
  }

  def readData(in: DataInput) {
    if (wqLog.isDebugEnabled) wqLog.debug("readData started")

    this.exchangeRef = new ExchangeRef()
    this.exchangeRef.readData(in)

    this.routing_key = in.readUTF()

    if (wqLog.isDebugEnabled) wqLog.debug("readData finished")
  }
}