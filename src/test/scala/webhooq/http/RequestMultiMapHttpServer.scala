package webhooq.http

import webhooq.logging.WebhooqLogger
import java.util.regex.Pattern
import java.util.UUID
import webhooq.ConcurrentMultiMap


class RequestMultiMapHttpServer( val name:String,  val port:Int, val callback:HttpRequest => HttpResponse = (request:HttpRequest) => HttpResponse(HttpStatus.NO_CONTENT(),List[(String,String)](HttpHeader.CONTENT_LENGTH.name -> 0.toString))) extends WebhooqLogger {
  val requests = new ConcurrentMultiMap[UUID,HttpRequest]()

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

    requests.put(uuid,request)

    val response = callback(request)

    response
  }
  val server = new HttpServer(name, port, _callback)
}
object RequestMultiMapHttpServer{
  val FIRST_UUID_IN_URI_PATH = Pattern.compile("^/(([0-9A-Fa-f]{8})-([0-9A-Fa-f]{4})-([0-9A-Fa-f]{4})-([0-9A-Fa-f]{4})-([0-9A-Fa-f]{12}))/?.*$")
}

