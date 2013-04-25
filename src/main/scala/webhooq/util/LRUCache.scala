package webhooq.util

import java.util.{Collections, LinkedHashMap}
import java.util.Map.Entry


class LRUCache[K,V](maxSize:Int) extends Map[K,V] {
  val cache:java.util.Map[K,V] = Collections.synchronizedMap(new LinkedHashMap[K,V](maxSize+1, .75F, true) {
    override def removeEldestEntry(eldest: Entry[K, V]): Boolean = size() > maxSize
  })

  def get(key: K): Option[V] = Option(cache.get(key))

  def iterator: Iterator[(K, V)] = new Iterator[(K, V)]{
    val i = cache.entrySet().iterator()
    def hasNext: Boolean = i.hasNext
    def next(): (K, V) = {
      val e = i.next()
      (e.getKey, e.getValue)
    }
  }

  def -(key: K): Map[K, V] = {
    cache.remove(key)
    this
  }

  def +[B1 >: V](kv: (K, B1)): Map[K, B1] = {
    cache.put(kv._1, kv._2.asInstanceOf[V])
    this
  }
}
