package webhooq.http.netty

import java.net.SocketAddress

import org.jboss.netty.bootstrap.ServerBootstrap
import org.jboss.netty.channel.ChannelFactory
import org.jboss.netty.channel.ChannelPipeline
import org.jboss.netty.channel.ChannelPipelineFactory
import org.jboss.netty.channel.Channels
import org.jboss.netty.channel.group.ChannelGroup
import org.jboss.netty.handler.codec.http.{HttpChunkAggregator, HttpServerCodec}



class NettyServer(val factory:ChannelFactory, allChannels:ChannelGroup , address:SocketAddress , requestHandler:RequestHandler) {
  val bootstrap:ServerBootstrap =  new ServerBootstrap(factory)

  def startUp() = {
    bootstrap.setPipelineFactory(new ChannelPipelineFactory() {
      def getPipeline(): ChannelPipeline = {
        return Channels.pipeline(new HttpServerCodec(), new HttpChunkAggregator(65536), requestHandler)
      }
    })
    bootstrap.setOption("child.tcpNoDelay", true)
    bootstrap.setOption("child.keepAlive", true)
    val channel = bootstrap.bind(address)
    allChannels.add(channel)
  }

  def shutDown() = {
    allChannels.close().awaitUninterruptibly()
    factory.releaseExternalResources()
  }
}