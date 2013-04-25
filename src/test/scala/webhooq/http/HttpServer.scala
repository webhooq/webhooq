package webhooq.http

import org.jboss.netty.channel.group.{DefaultChannelGroup, ChannelGroup}
import java.net.{InetSocketAddress, SocketAddress}
import org.jboss.netty.channel.{ChannelFuture, ChannelFutureListener, MessageEvent, ExceptionEvent, ChannelStateEvent, ChannelHandlerContext, SimpleChannelHandler, Channels, ChannelPipeline, ChannelPipelineFactory, ChannelFactory}
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory
import java.util.concurrent.Executors
import org.jboss.netty.bootstrap.ServerBootstrap
import org.jboss.netty.handler.codec.http.{HttpHeaders, HttpChunkAggregator, HttpServerCodec}
import webhooq.logging.WebhooqLogger
import webhooq.http.netty.NettyHttpConverters._


class HttpServer(val name:String, val port:Int, val callback:HttpRequest => HttpResponse) extends WebhooqLogger {
  val channelGroup:ChannelGroup = new DefaultChannelGroup(name)
  val socketAddress:SocketAddress = new InetSocketAddress(port)
  val channelFactory:ChannelFactory = new NioServerSocketChannelFactory(Executors.newCachedThreadPool(), Executors.newCachedThreadPool())

  val bootstrap:ServerBootstrap =  new ServerBootstrap(channelFactory)

  bootstrap.setPipelineFactory(new ChannelPipelineFactory() {
    def getPipeline: ChannelPipeline = {
      Channels.pipeline(new HttpServerCodec(), new HttpChunkAggregator(65536), new SimpleChannelHandler() {

        override def channelOpen(ctx:ChannelHandlerContext, e:ChannelStateEvent ) {
          channelGroup.add(e.getChannel)
        }

        override def exceptionCaught(ctx:ChannelHandlerContext, e:ExceptionEvent) {
          wqLog.error("while receiving",e.getCause)
          e.getChannel.close
        }

        override def messageReceived(ctx: ChannelHandlerContext, e: MessageEvent) {
          val request:HttpRequest =
            e.getMessage.asInstanceOf[org.jboss.netty.handler.codec.http.HttpRequest]
          wqLog.debug("webhook callback server received message: %s".format(request))


          val response:org.jboss.netty.handler.codec.http.HttpResponse = callback(request)

          e.getChannel.write(response).addListener(new ChannelFutureListener() {
            def operationComplete(future:ChannelFuture ) {
              future.getChannel.close()
            }
          })
        }
      })
    }
  })
  bootstrap.setOption("child.tcpNoDelay", true)
  bootstrap.setOption("child.keepAlive", true)
  val channel = bootstrap.bind(socketAddress)
  channelGroup.add(channel)



  override def finalize() {
    channelGroup.close().awaitUninterruptibly()
    channelFactory.releaseExternalResources()
    super.finalize()
  }
}
