package webhooq

import org.junit.runner.RunWith
import org.specs.runner.{JUnit, JUnitSuiteRunner}
import org.specs.Specification
import webhooq.logging.WebhooqLogger
import java.util.UUID


@RunWith(classOf[JUnitSuiteRunner])
class ConcurrentMultiMapSpec extends Specification with WebhooqLogger with JUnit {
  "A Concurrent MultiMap " should {
    "Store 10 values inserted by 10 threads, inserted at the same time" in {
      val cmmap = new ConcurrentMultiMap[UUID,String]()
      val uuid = UUID.randomUUID()
      val count = System.getProperty("webhooq.ConcurrentMultiMapSpec.count","100").toInt
      val workers = (0 to count-1).map { i =>
        val threadName = "Thread "+i
        val lock = new Object
        (lock, new PutThread(threadName, lock, uuid, cmmap))
      }

      workers.foreach { case (_, worker) =>
        worker.start()
      }
      workers.foreach { case (lock, _) =>
        lock.synchronized{
          lock.wait(100)
        }
      }
      val values = cmmap.values(uuid)
      values.foldLeft(0) { (i,s) =>
        wqLog.info("value[%d]: %s".format(i,s))
        i+1
      }
      values.size must_==(count)

    }
  }
}

case class PutThread(val threadName:String, val lock:Object, val uuid:UUID, val location:ConcurrentMultiMap[UUID,String]) extends Thread {
  override def run() {
    location.put(uuid,threadName)
    lock.synchronized {
      lock.notify()
    }
  }
}