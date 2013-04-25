package webhooq.http.netty

import webhooq.http.{HttpStatus, HttpMethod, HttpRequest, HttpResponse}
import java.net.URI
import scala.collection.JavaConverters.collectionAsScalaIterableConverter
import org.jboss.netty.handler.codec.http.{QueryStringDecoder, HttpVersion, DefaultHttpRequest, DefaultHttpResponse}
import org.jboss.netty.buffer.ChannelBuffers

/**
 * Converters for moving Http 1.1 Request/Response instances into and out of of Netty.
 */
object NettyHttpConverters {
  implicit def nettyRequestToWebhooqRequest(nettyRequest:org.jboss.netty.handler.codec.http.HttpRequest): HttpRequest = {
    val content  = nettyRequest.getContent
    val size     = content.readableBytes()
    val body    = new Array[Byte](size)
    val parameters =  collectionAsScalaIterableConverter(new QueryStringDecoder(nettyRequest.getUri).getParameters.entrySet()).asScala.map(  e => (e.getKey,collectionAsScalaIterableConverter(e.getValue).asScala.toList)).toList
    val headers  = collectionAsScalaIterableConverter(nettyRequest.getHeaders).asScala.map {entry => (entry.getKey, entry.getValue)}.toList
    nettyRequest.getContent.readBytes(body,0,size)
    new HttpRequest(nettyRequest.getMethod,new URI(nettyRequest.getUri),headers,body,parameters)
  }

  implicit def webhooqRequestToNettyRequest(request:HttpRequest):org.jboss.netty.handler.codec.http.HttpRequest = {
    // TODO:  QueryStringEncoder ?!?
    val nettyRequest = new DefaultHttpRequest(HttpVersion.HTTP_1_1, request.method, request.uri.toASCIIString)
    request.headers.foreach{case (header,value) => nettyRequest.addHeader(header,value)}
    nettyRequest.setContent(ChannelBuffers.copiedBuffer(request.body))
    nettyRequest
  }

  implicit def nettyResponseToWebhooqResponse(nettyResponse:org.jboss.netty.handler.codec.http.HttpResponse): HttpResponse = {
    val content  = nettyResponse.getContent
    val size     = content.readableBytes()
    val body    = new Array[Byte](size)
    val headers  = collectionAsScalaIterableConverter(nettyResponse.getHeaders).asScala.map {entry => (entry.getKey, entry.getValue)}.toList
    nettyResponse.getContent.readBytes(body,0,size)
    new HttpResponse(nettyResponse.getStatus, headers, body)
  }

  implicit def webhooqResponseToNettyResponse(response:HttpResponse):org.jboss.netty.handler.codec.http.HttpResponse = {
    val nettyResponse = new DefaultHttpResponse(HttpVersion.HTTP_1_1, response.status )
    response.headers.foreach{case (header,value) => nettyResponse.addHeader(header,value)}
    nettyResponse.setContent(ChannelBuffers.copiedBuffer(response.body))
    nettyResponse
  }

  implicit def nettyMethodToWebhookMethod(nettyMethod:org.jboss.netty.handler.codec.http.HttpMethod):HttpMethod = {
    nettyMethod match {
      case org.jboss.netty.handler.codec.http.HttpMethod.OPTIONS => HttpMethod.OPTIONS
      case org.jboss.netty.handler.codec.http.HttpMethod.GET     => HttpMethod.GET
      case org.jboss.netty.handler.codec.http.HttpMethod.HEAD    => HttpMethod.HEAD
      case org.jboss.netty.handler.codec.http.HttpMethod.POST    => HttpMethod.POST
      case org.jboss.netty.handler.codec.http.HttpMethod.PUT     => HttpMethod.PUT
      case org.jboss.netty.handler.codec.http.HttpMethod.PATCH   => HttpMethod.PATCH
      case org.jboss.netty.handler.codec.http.HttpMethod.DELETE  => HttpMethod.DELETE
      case org.jboss.netty.handler.codec.http.HttpMethod.TRACE   => HttpMethod.TRACE
      case org.jboss.netty.handler.codec.http.HttpMethod.CONNECT => HttpMethod.CONNECT
    }
  }
  implicit def webhookMethodToNettyMethod(method:HttpMethod):org.jboss.netty.handler.codec.http.HttpMethod = {
    method match {
      case HttpMethod.OPTIONS => org.jboss.netty.handler.codec.http.HttpMethod.OPTIONS
      case HttpMethod.GET     => org.jboss.netty.handler.codec.http.HttpMethod.GET
      case HttpMethod.HEAD    => org.jboss.netty.handler.codec.http.HttpMethod.HEAD
      case HttpMethod.POST    => org.jboss.netty.handler.codec.http.HttpMethod.POST
      case HttpMethod.PUT     => org.jboss.netty.handler.codec.http.HttpMethod.PUT
      case HttpMethod.PATCH   => org.jboss.netty.handler.codec.http.HttpMethod.PATCH
      case HttpMethod.DELETE  => org.jboss.netty.handler.codec.http.HttpMethod.DELETE
      case HttpMethod.TRACE   => org.jboss.netty.handler.codec.http.HttpMethod.TRACE
      case HttpMethod.CONNECT => org.jboss.netty.handler.codec.http.HttpMethod.CONNECT
    }
  }

  implicit def nettyStatusToWebhookStatus(nettyStatus:org.jboss.netty.handler.codec.http.HttpResponseStatus):HttpStatus = {
    new HttpStatus(nettyStatus.getCode, nettyStatus.getReasonPhrase)


  }

  implicit def webhookStatusToNettyStatus(status:HttpStatus):org.jboss.netty.handler.codec.http.HttpResponseStatus = {
    new org.jboss.netty.handler.codec.http.HttpResponseStatus(status.code,status.reasonPhrase)
  }

}
