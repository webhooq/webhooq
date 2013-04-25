package webhooq.http


abstract class HttpMethod(val name:String) {}
object HttpMethod {
  case object OPTIONS extends HttpMethod("OPTIONS")
  case object GET     extends HttpMethod("GET")
  case object HEAD    extends HttpMethod("HEAD")
  case object POST    extends HttpMethod("POST")
  case object PUT     extends HttpMethod("PUT")
  case object PATCH   extends HttpMethod("PATCH")
  case object DELETE  extends HttpMethod("DELETE")
  case object TRACE   extends HttpMethod("TRACE")
  case object CONNECT extends HttpMethod("CONNECT")

  def parse(input:String):Option[HttpMethod] = parse(Option(input))
  def parse(input:Option[String]):Option[HttpMethod] = input match {
    case Some(OPTIONS.name) => Some(OPTIONS)
    case Some(GET.name)     => Some(GET)
    case Some(HEAD.name)    => Some(HEAD)
    case Some(POST.name)    => Some(POST)
    case Some(PUT.name)     => Some(PUT)
    case Some(PATCH.name)   => Some(PATCH)
    case Some(DELETE.name)  => Some(DELETE)
    case Some(TRACE.name)   => Some(TRACE)
    case Some(CONNECT.name) => Some(CONNECT)
    case _ => None
  }
}
