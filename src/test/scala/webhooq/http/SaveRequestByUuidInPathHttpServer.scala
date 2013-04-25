package webhooq.http

import webhooq.logging.WebhooqLogger
import java.util.UUID
import java.util.regex.Pattern


class SaveRequestByUuidInPathHttpServer( val name:String,  val port:Int, val callback:HttpRequest => HttpResponse = (request:HttpRequest) => HttpResponse(HttpStatus.NO_CONTENT(),List[(String,String)](HttpHeader.CONTENT_LENGTH.name -> 0.toString))) extends WebhooqLogger {
  val lock = new Object
  val requests = new java.util.concurrent.ConcurrentHashMap[UUID,HttpRequest]()

  val _callback = (request:HttpRequest) => {
    val matcher = SaveRequestByUuidInPathHttpServer.FIRST_UUID_IN_URI_PATH.matcher(request.uri.getPath)
    val uuid = {
      if(matcher.matches) {
        try{Option(UUID.fromString(matcher.group(1)))}
        catch {case t:Throwable => Option.empty[UUID]}}
      else Option.empty[UUID]
    }.getOrElse({
      val uuid = UUID.randomUUID()
      wqLog.warn("Shitty, couldn't parse UUID from the URI's path, going with %s instead".format(uuid.toString))
      uuid
    })

    requests.put(uuid,request)

    val response = callback(request)
    lock.synchronized {
      lock.notifyAll()
    }

    response
  }
  val server = new HttpServer(name, port, _callback)

}
object SaveRequestByUuidInPathHttpServer {
  val FIRST_UUID_IN_URI_PATH = Pattern.compile("^/(([0-9A-Fa-f]{8})-([0-9A-Fa-f]{4})-([0-9A-Fa-f]{4})-([0-9A-Fa-f]{4})-([0-9A-Fa-f]{12}))/?.*$")
}