package webhooq.http


case class HttpHeader(val name:String)
object HttpHeader {
  val ACCEPT = HttpHeader("Accept")
  val CONTENT_LENGTH = HttpHeader("Content-Length")
  val CONTENT_TYPE = HttpHeader("Content-Type")
  val HOST = HttpHeader("Host")
}