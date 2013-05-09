package webhooq.http

import java.net.URI
import webhooq.util.Utils

case class HttpRequest(val method:HttpMethod, val uri:URI, val headers:List[(String,String)] = List.empty[(String,String)], val body:Array[Byte] = Array.empty[Byte], val queryParameters:List[(String,List[String])] = List.empty[(String,List[String])]) {
  val md5:String = Utils.md5(this.body)

  def getHeader(headerName:String):Option[String] ={
    headers.find{case (header,_) => headerName == header}.map(_._2)
  }

  override def toString: String = {
    val sb = new StringBuilder
    sb.append("method: ").append(this.method.name).append("; ")
    sb.append("URI: ").append(this.uri.toASCIIString).append("; ")
    sb.append("headers: ").append(this.headers.mkString(",")).append("; ")
    sb.append("query-params: ").append(this.queryParameters.mkString(",")).append("; ")
    sb.append("body: MD5(").append(Utils.md5(this.body)).append("); ")
    sb.toString
  }
}