package webhooq.model.dao

import com.hazelcast.nio.DataSerializable
import java.io.{DataOutput, DataInput}
import webhooq.logging.WebhooqLogger
import webhooq.util.Utils
import Utils._
import java.security.MessageDigest

class Message (var body:Array[Byte]) extends DataSerializable with WebhooqLogger {
  def this() {
    this(Array.empty[Byte])
  }
  def writeData(out: DataOutput): Unit = {
    if (wqLog.isDebugEnabled) wqLog.debug("writeData started")
    out.writeInt(body.length)
    out.write(body)
    if (wqLog.isDebugEnabled) wqLog.debug("writeData finished")
  }

  def readData(in: DataInput): Unit = {
    if (wqLog.isDebugEnabled) wqLog.debug("readData started")
    val len = in.readInt()
    this.body = new Array[Byte](len)
    in.readFully(this.body,0,len)
    if (wqLog.isDebugEnabled) wqLog.debug("readData finished")
  }

  override def toString():String = {
    val digest = MessageDigest.getInstance("MD5")
    digest.update(this.body)
    val hexDigest = array2hex(digest.digest())

    "Message(bytes: %d, md5: %s)".format(this.body.length, hexDigest)
  }


  def toMD5():String = {
    val digest = MessageDigest.getInstance("MD5")
    digest.update(this.body)
    "MD5:"+array2hex(digest.digest())
  }

  def toVerbose():String = {
    if (this.body.length == 0) "<EMPTY MESSAGE BODY>"
    else array2hex(this.body)
  }
}
