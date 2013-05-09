package webhooq

import org.junit.runner.RunWith
import org.specs.runner.{JUnit, JUnitSuiteRunner}
import org.specs.Specification
import webhooq.logging.WebhooqLogger
import java.util.{Comparator, UUID}
import akka.util.ConcurrentMultiMap

@RunWith(classOf[JUnitSuiteRunner])
class ConcurrentMultiMapSpec extends Specification with WebhooqLogger with JUnit {
  object UuidComparator extends Comparator[UUID] {
    def compare(o1: UUID, o2: UUID): Int = o1.toString.compareTo(o2.toString)
  }
  object StringComparator extends Comparator[String] {
    def compare(o1: String, o2: String): Int = o1.compareTo(o2)
  }
  val count = System.getProperty("webhooq.ConcurrentMultiMapSpec.count","100").toInt
  "A Concurrent MultiMap " should {
    "Store %d values inserted by %d threads, inserted at the same time".format(count,count) in {
      val cmmap = new ConcurrentMultiMap[UUID,String](1, StringComparator)
      val uuid = UUID.randomUUID()
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
      val values = cmmap.valueIterator(uuid).toList
      values.foldLeft(0) { (i,s) =>
        wqLog.info("value[%d]: %s".format(i,s))
        i+1
      }
      values.size must_==(count)

    }

    "Store three values for the same key" in {
      val map   = new ConcurrentMultiMap[UUID,UUID](1, UuidComparator)
      val key   = UUID.randomUUID()
      val val_1 = UUID.randomUUID()
      val val_2 = UUID.randomUUID()
      val val_3 = UUID.randomUUID()

      map.put(key,val_1)
      map.put(key,val_2)
      map.put(key,val_3)

      val vals = map.valueIterator(key).toList

      vals.size must_== 3

      vals.foldLeft(0) { (i,s) =>
        wqLog.info("value[%d]: %s".format(i,s))
        i+1
      }

      vals.mustContain(val_1)
      vals.mustContain(val_2)
      vals.mustContain(val_3)


    }


    "Store the same three values for two seperate keys" in {
      val map   = new ConcurrentMultiMap[UUID,UUID](2, UuidComparator)
      val key_1 = UUID.randomUUID()
      val key_2 = UUID.randomUUID()
      val val_1 = UUID.randomUUID()
      val val_2 = UUID.randomUUID()
      val val_3 = UUID.randomUUID()

      map.put(key_1,val_1)
      map.put(key_1,val_2)
      map.put(key_1,val_3)
      map.put(key_2,val_1)
      map.put(key_2,val_2)
      map.put(key_2,val_3)

      val vals_1 = map.valueIterator(key_1).toList
      val vals_2 = map.valueIterator(key_2).toList

      vals_1.size must_== 3
      vals_2.size must_== 3

      vals_1.foldLeft(0) { (i,s) =>
        wqLog.info("value_1[%d]: %s".format(i,s))
        i+1
      }
      vals_2.foldLeft(0) { (i,s) =>
        wqLog.info("value_2[%d]: %s".format(i,s))
        i+1
      }

      vals_1.mustContain(val_1)
      vals_1.mustContain(val_2)
      vals_1.mustContain(val_3)
      vals_2.mustContain(val_1)
      vals_2.mustContain(val_2)
      vals_2.mustContain(val_3)
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