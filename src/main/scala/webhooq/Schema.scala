package webhooq

import com.hazelcast.core.{Transaction, HazelcastInstance}
import model.dao._
import model.dao.Incoming
import model.dao.Message
import com.hazelcast.nio.DataSerializable
import java.util.concurrent.{TimeoutException, TimeUnit}

/**
 * holds a reference to each of the hazelcast collections we use in the application.
 */
object Schema extends Hazelcast {
  val messages: java.util.Map[MessageRef, Message] = hazelcast.getMap("webhooq.messages")
  val incoming: java.util.concurrent.BlockingQueue[Incoming] = hazelcast.getQueue("webhooq.incoming")
  val outgoing: java.util.concurrent.BlockingQueue[Outgoing] = hazelcast.getQueue("webhooq.outgoing")
  val bindings: com.hazelcast.core.MultiMap[ExchangeRef, Binding] = hazelcast.getMultiMap("webhooq.bindings")
  val deliveries: com.hazelcast.core.MultiMap[DeliveryRef, Delivery] = hazelcast.getMultiMap("webhooq.deliveries")
  val exchange_arguments: com.hazelcast.core.MultiMap[ExchangeRef,Argument] = hazelcast.getMultiMap("webhooq.exchange.args")
  val queue_arguments: com.hazelcast.core.MultiMap[QueueRef,Argument] = hazelcast.getMultiMap("webhooq.queue.args")
//  val bind_arguments: com.hazelcast.core.MultiMap[String,String] = hazelcast.getMultiMap("webhooq.bind.args")
  def tx[A](f: => A):A = {
    val txn = hazelcast.getTransaction()
    txn.begin()
    try {
      val r = f
      txn.commit()
      r
    } catch {
      case t:Throwable =>
        txn.rollback()
        throw t
    }
  }

  /**
   *
   * @param key The key to lock on.
   * @param timeoutMillis the maximum time to wait for the lock, in millis.
   * @param lockCallback the callback to be executed while the lock is held.
   * @tparam A
   * @tparam B
   * @return te result of the
   */
  def lock[A,B <: DataSerializable](key:B, timeoutMillis:Long)(lockCallback: => A):A = {
    val lock = hazelcast.getLock(key)
    if (lock.tryLock(timeoutMillis, TimeUnit.MILLISECONDS)) {
      try { lockCallback } finally { lock.unlock() }
    } else {
      throw new TimeoutException("Could not lock key '%s' within %d milliseconds".format(key.toString, timeoutMillis))
    }

  }
}
