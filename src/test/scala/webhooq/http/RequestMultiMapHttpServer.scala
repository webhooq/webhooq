package webhooq.http

import webhooq.logging.WebhooqLogger
import java.util.regex.Pattern
import java.util.UUID
import akka.util.ConcurrentMultiMap
import webhooq.util.Utils._

class RequestMultiMapHttpServer( val name:String,  val mapSize:Int, val port:Int, val callback:HttpRequest => HttpResponse = (request:HttpRequest) => HttpResponse(HttpStatus.NO_CONTENT(),List[(String,String)](HttpHeader.CONTENT_LENGTH.name -> 0.toString))) extends WebhooqLogger {

  object HttpRequestComparator extends java.util.Comparator[HttpRequest] {
    def compare(o1: HttpRequest, o2: HttpRequest): Int = {
      if (o2 == null) throw new NullPointerException("Can not be compared to null!")
      lazy val m = o1.method.name.compareTo(o2.method.name)
      lazy val u = o1.uri.compareTo(o2.uri)
      lazy val b = o1.md5.compareTo(o2.md5)
      List(m,u,b).foldLeft(0) {(acc,comp) => if (acc!=0) acc else comp}
    }
  }

  val requests = new ConcurrentMultiMap[UUID,HttpRequest](mapSize,HttpRequestComparator)

  val _callback = (request:HttpRequest) => {
    val matcher = SaveRequestByUuidInPathHttpServer.FIRST_UUID_IN_URI_PATH.matcher(request.uri.getPath)
    val uuid = {
      if(matcher.matches) {
        try{Option(UUID.fromString(matcher.group(1)))}
        catch {case t:Throwable => Option.empty[UUID]}}
      else Option.empty[UUID]
    }.getOrElse({
      val uuid = UUID.randomUUID()
      wqLog.warn("Shitty, couldn't parse UUID from the URI's path, going with %s instead, which is probably not what you want!".format(uuid.toString))
      uuid
    })
    wqLog.info("Putting '%s' with a value of: %s".format(uuid,request.toString))
    requests.put(uuid,request)

    requests.valueIterator(uuid).foldLeft(0) { (i,s) =>
      wqLog.info("%s value[%d]: %s".format(uuid.toString,i,s))
      i+1
    }

    val response = callback(request)

    response
  }
  val server = new HttpServer(name, port, _callback)
}
object RequestMultiMapHttpServer{
  val FIRST_UUID_IN_URI_PATH = Pattern.compile("^/(([0-9A-Fa-f]{8})-([0-9A-Fa-f]{4})-([0-9A-Fa-f]{4})-([0-9A-Fa-f]{4})-([0-9A-Fa-f]{12}))/?.*$")
}

