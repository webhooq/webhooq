package webhooq.http

import java.net.URI
import webhooq.util.Utils
import Utils._
case class HttpRequest(
  method:HttpMethod,
  uri:URI,
  headers:List[(String,String)] = List.empty[(String,String)],
  body:Array[Byte] = Array.empty[Byte],
  queryParameters:List[(String,List[String])] = List.empty[(String,List[String])]
) {

  def getHeader(headerName:String):Option[String] ={
    headers.find{case (header,_) => headerName == header}.map(_._2)
  }

  override def toString: String = {
    val sb = new StringBuilder
    sb.append("method: ").append(this.method.name).append("; ")
    sb.append("URI: ").append(this.uri.toASCIIString).append("; ")
    sb.append("headers: ").append(this.headers.mkString(",")).append("; ")
    sb.append("query-params: ").append(this.queryParameters.mkString(",")).append("; ")
    sb.append("body: MD5(").append(md5(this.body)).append("); ")
    sb.toString
  }
}