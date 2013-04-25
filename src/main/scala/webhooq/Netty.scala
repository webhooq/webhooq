package webhooq

import org.jboss.netty.logging.{Log4JLoggerFactory, InternalLoggerFactory}
import webhooq.http.netty.{RequestHandler, NettyServer}
import webhooq.logging.WebhooqLogger
import org.jboss.netty.channel.group.{DefaultChannelGroup, ChannelGroup}
import java.net.{InetSocketAddress, SocketAddress}
import org.jboss.netty.channel.ChannelFactory
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory
import java.util.concurrent.Executors


trait Netty extends WebhooqLogger {
  wqLog.info("Configuring Netty")
  wqLog.info("Telling Netty to use Log4j for it's logging")
  InternalLoggerFactory.setDefaultFactory(new Log4JLoggerFactory())
  val server = new NettyServer(Netty.channelFactory, Netty.channelGroup, Netty.socketAddress, new RequestHandler(Netty.channelGroup))
  server.startUp()
  wqLog.info("Netty initialized on %s".format(Netty.socketAddress.toString))
  Runtime.getRuntime.addShutdownHook(new Thread("Webhooq Shutdown Thread") {
    override def run() : Unit = {
      wqLog.info("Shutting down Netty")
      System.err.println("Shutting down Netty")
      server.shutDown()
    }
  })
}
object Netty {
  val channelGroup:ChannelGroup = new DefaultChannelGroup("webhooq-netty-server")
  val socketAddress:SocketAddress = new InetSocketAddress( Option(System.getProperty("netty.port")).map(s => try {s.toInt} catch {case e:NumberFormatException=>8080}).getOrElse(8080))
  val channelFactory:ChannelFactory = new NioServerSocketChannelFactory(Executors.newCachedThreadPool(), Executors.newCachedThreadPool())
}
