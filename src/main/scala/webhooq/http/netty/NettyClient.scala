package webhooq.http.netty

import org.jboss.netty.channel.{ChannelFuture, ChannelFutureListener, SimpleChannelHandler, MessageEvent, ExceptionEvent, ChannelStateEvent, ChannelHandlerContext, ChannelPipelineFactory, Channels, ChannelPipeline}
import org.jboss.netty.bootstrap.ClientBootstrap
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory
import org.jboss.netty.handler.ssl.SslHandler
import org.jboss.netty.handler.codec.http.{HttpChunkAggregator, HttpContentDecompressor, HttpClientCodec, HttpHeaders, HttpVersion, DefaultHttpRequest}
import java.net.InetSocketAddress
import java.util.concurrent.Executors
import java.security.Security
import java.security.cert.X509Certificate
import javax.net.ssl.{X509TrustManager, SSLContext}
import webhooq.logging.WebhooqLogger
import org.jboss.netty.channel.group.{DefaultChannelGroup, ChannelGroup}
import webhooq.http.netty.NettyHttpConverters._
import webhooq.http.{HttpRequest, HttpResponse}


object HttpClient extends WebhooqLogger {
  val channelGroup:ChannelGroup = new DefaultChannelGroup("wq-netty-client")

  def blockingCall(httpRequest:HttpRequest, timeoutMillis:Int):Option[HttpResponse] = {
    val lock = new Object
    var httpResponse = Option.empty[HttpResponse]
    val callback = (response:HttpResponse) => {
      httpResponse = Some(response)
      lock.synchronized {
        lock.notifyAll()
      }

    }
    call(httpRequest,callback)
    lock.synchronized {lock.wait(timeoutMillis)}
    httpResponse
  }

  def call(_request:HttpRequest, responseCallback:HttpResponse => Unit): Unit = {
    val request = ensureHostHeader(_request)
    val host = request.headers.find{case (header,_) => HttpHeaders.Names.HOST == header}.map(_._2).getOrElse("localhost")
    val scheme = Option(request.uri.getScheme).map(_.toLowerCase).getOrElse("http") match {
      case scheme@("http"|"https") => scheme
      case _ => throw new UnsupportedOperationException("Only HTTP(S) is supported.")
    }
    val port = (scheme,request.uri.getPort) match {
      case ("http" , -1  ) => 80
      case ("https", -1  ) => 443
      case (_      , port) => port
    }

    val ssl = scheme == "https"

    wqLog.debug("host: "+host)
    wqLog.debug("scheme: "+scheme)
    wqLog.debug("port: "+port)
    wqLog.debug("ssl: "+ssl)

    val bootstrap = new ClientBootstrap(
      new NioClientSocketChannelFactory(Executors.newCachedThreadPool(), Executors.newCachedThreadPool())
    )

    bootstrap.setPipelineFactory(new NettyClientPipelineFactory(ssl, channelGroup, responseCallback))
    val connectionFuture = bootstrap.connect(new InetSocketAddress(host, port))
    connectionFuture.addListener(new ChannelFutureListener() with WebhooqLogger {
      def operationComplete(connectedFuture: ChannelFuture) {
        if (!connectedFuture.isSuccess) {
          wqLog.warn("error while attempting to connect to %s".format(request.uri.toASCIIString),connectedFuture.getCause)
          //bootstrap.releaseExternalResources()
        } else {
          val channel = connectedFuture.getChannel
          val nettyRequest = webhooqRequestToNettyRequest(request)
          val writingFuture = channel.write(nettyRequest)
          writingFuture.addListener(new ChannelFutureListener() with WebhooqLogger {
            def operationComplete(writeFuture: ChannelFuture) {
              if (!writeFuture.isSuccess) {
                wqLog.warn("error while attempting to write to %s".format(request.uri.toASCIIString),writeFuture.getCause)
                //bootstrap.releaseExternalResources()
              } else {
                val disconnectionFuture  = channel.getCloseFuture
                disconnectionFuture.addListener(new ChannelFutureListener() with WebhooqLogger {
                  def operationComplete(diconnectFuture: ChannelFuture) {
                    if (!diconnectFuture.isSuccess) {
                      wqLog.warn("error while attempting to disconnect from %s".format(request.uri.toASCIIString),diconnectFuture.getCause)
                    }
                    //bootstrap.releaseExternalResources()
                  }
                })
              }
            }
          })
        }
      }
    })
//    nettyRequest.setHeader(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.CLOSE);
//    nettyRequest.setHeader(HttpHeaders.Names.ACCEPT_ENCODING, HttpHeaders.Values.GZIP);
  }

  def ensureHostHeader(request:HttpRequest):HttpRequest = {
    request.headers.find{case (header,_) => HttpHeaders.Names.HOST == header} match {
      case None =>
        val host = Option(request.uri.getHost).map(_.toLowerCase).getOrElse("localhost")
        new HttpRequest(request.method, request.uri, request.headers :+ (HttpHeaders.Names.HOST,host), request.body)
      case Some(_) =>
        request
    }
  }

}

class NettyClientPipelineFactory(ssl:Boolean, val channelGroup:ChannelGroup, responseCallback: HttpResponse => Unit ) extends ChannelPipelineFactory with WebhooqLogger {
  def getPipeline: ChannelPipeline = {
    val pipeline = Channels.pipeline()//super.getPipeline

    if (ssl) {
      val  engine = SslContextFactory.CLIENT_CONTEXT.createSSLEngine()
      engine.setUseClientMode(true)

      pipeline.addLast("ssl", new SslHandler(engine))
    }

    pipeline.addLast("codec", new HttpClientCodec())
    pipeline.addLast("inflater", new HttpContentDecompressor())
    pipeline.addLast("aggregator", new HttpChunkAggregator(1048576))
    pipeline.addLast("response-handler", new NettyResponseHandler(channelGroup, responseCallback))

    pipeline
  }
}
case class NettyResponseHandler(val channelGroup:ChannelGroup, val responseCallback: HttpResponse => Unit) extends SimpleChannelHandler  with WebhooqLogger {
  override def channelOpen(ctx:ChannelHandlerContext, e:ChannelStateEvent ):Unit = {
    wqLog.debug("ResponseHandler.channelOpen")
    channelGroup.add(e.getChannel)
  }

  override def exceptionCaught(ctx:ChannelHandlerContext, e:ExceptionEvent):Unit = {
    wqLog.warn("ResponseHandler.exceptionCaught", e.getCause)
    e.getChannel.close()
  }

  override def messageReceived(ctx:ChannelHandlerContext, e:MessageEvent):Unit = {
    wqLog.debug("ResponseHandler.messageReceived")
    val response = e.getMessage.asInstanceOf[org.jboss.netty.handler.codec.http.HttpResponse]
    responseCallback(response)
  }
}


object SslContextFactory {
  val PROTOCOL = "TLS";
  val algorithm = Option( Security.getProperty("ssl.KeyManagerFactory.algorithm")).getOrElse("SunX509")
  val CLIENT_CONTEXT = SSLContext.getInstance(PROTOCOL);

  val trustManager = new X509TrustManager() {
    def checkClientTrusted(chain: Array[X509Certificate], authType: String) {
      // Always trust
    }

    def checkServerTrusted(chain: Array[X509Certificate], authType: String) {
      // Always trust
    }

    def getAcceptedIssuers: Array[X509Certificate] = Array.empty[X509Certificate]
  }

  try {CLIENT_CONTEXT.init(null, Array(trustManager), null)}
  catch {
    case t:Throwable => throw new java.lang.Error("Failed to initialize the client-side SSLContext",t)
  }
}