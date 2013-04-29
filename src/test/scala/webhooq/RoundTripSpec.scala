package webhooq

import java.net.{URI, InetSocketAddress, SocketAddress}
import java.util.concurrent.Executors
import org.junit.runner.RunWith
import org.specs.runner.{JUnit, JUnitSuiteRunner}
import org.specs.Specification
import org.jboss.netty.channel.group.{DefaultChannelGroup, ChannelGroup}
import org.jboss.netty.channel.{ChannelFuture, ChannelFutureListener, ExceptionEvent, ChannelStateEvent, MessageEvent, ChannelHandlerContext, SimpleChannelHandler, Channels, ChannelPipeline, ChannelPipelineFactory, ChannelFactory}
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory
import org.jboss.netty.bootstrap.ServerBootstrap
import org.jboss.netty.handler.codec.http.{HttpHeaders, HttpChunkAggregator, HttpServerCodec}
import webhooq.logging.WebhooqLogger
import webhooq.util.Utils
import Utils._
import webhooq.http.{HttpHeader, HttpMethod, HttpStatus, HttpResponse, HttpRequest}
import webhooq.http.netty.NettyHttpConverters._
import java.util.UUID
import webhooq.http.netty.{RequestHandler, HttpClient}
import webhooq.model.{Topic, Type}
import webhooq.http.WebhooqHeader._
/**
 */
@RunWith(classOf[JUnitSuiteRunner])
class RoundTripSpec extends Specification with WebhooqLogger with JUnit {
  val testExchangeId  = UUID.randomUUID
  val testQueueId     = UUID.randomUUID
  val testMessageId   = UUID.randomUUID
  val testMessageBody = UUID.randomUUID().toString.getBytes("UTF-8")

  "A Round Trip" should {
    val requests = new java.util.concurrent.ConcurrentHashMap[UUID,HttpRequest]()
    val lock = new Object

    "just work" in {

//--- Start a call back server on port 8378 ---
      val channelGroup:ChannelGroup = new DefaultChannelGroup("http-server")
      val socketAddress:SocketAddress = new InetSocketAddress(8378)
      val channelFactory:ChannelFactory = new NioServerSocketChannelFactory(Executors.newCachedThreadPool(), Executors.newCachedThreadPool())

      val bootstrap:ServerBootstrap =  new ServerBootstrap(channelFactory)

      try {
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
                val messageId = RequestHandler.parseMessageId(request) match {
                  case RequestHandler.NotFound  => throw  new IllegalStateException("couldn't locate '%s' header in request!".format(MESSAGE_ID.name))
                  case RequestHandler.Malformed(msg) => throw new IllegalStateException("Header '%s' was malformed in request: %s".format(MESSAGE_ID.name, msg))
                  case RequestHandler.Success(id) => id
                }

                requests.put(messageId,request)

                // wake up the test
                lock.synchronized {
                  lock.notifyAll()
                }

                val response:org.jboss.netty.handler.codec.http.HttpResponse =
                  new HttpResponse(HttpStatus.NO_CONTENT(),List[(String,String)](HttpHeaders.Names.CONTENT_LENGTH ->  0.toString))

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


//--- Start a webhooq server on port 4667 ---
        System.setProperty("netty.port", 4667.toString)
        Main.main(Array.empty[String])


//--- Declare an exchange using http ---
        val declareExchangeTimeout = 3
        val declareExchangeUrl = new URI("http://localhost:4667/exchange/test-%s?%s=%s".format(testExchangeId.toString, Type.name, Topic.name))
        val declareExchangeRequest = new HttpRequest(HttpMethod.POST,declareExchangeUrl)
        HttpClient.blockingCall(declareExchangeRequest, declareExchangeTimeout * 1000) match {
          case None =>
            fail("Declare Exchange response timed out after %d seconds ".format(declareExchangeTimeout))
          case Some(response) if (response.status.code != HttpStatus.CREATED().code) =>
            fail("Declare Exchange response did not return expected 201 Created status: %s".format(response))
          case Some(response) =>
            wqLog.info("Declare Exchange successfully created")


        }


//--- Declare an queue using http
        val declareQueueTimeout = 3
        val declareQueueUrl = new URI("http://localhost:4667/queue/test-%s".format(testQueueId.toString))
        val declareQueueRequest = new HttpRequest(HttpMethod.POST,declareQueueUrl)
        HttpClient.blockingCall(declareQueueRequest,declareQueueTimeout*1000) match {
          case None =>
            fail("Declare Queue response timed out after %d seconds ".format(declareQueueTimeout))
          case Some(response) if (response.status.code != HttpStatus.CREATED().code) =>
            fail("Declare Queue response did not return expected 201 Created status: %s".format(response))
          case Some(response) =>
            wqLog.info("Declare Queue successfully created")

        }


//--- Bind a queue to the exchange using http
        val bindTimeout = 3
        val bindUrl = new URI("http://localhost:4667/exchange/test-%s/bind".format(testExchangeId.toString))
        val bindCallbackUrl = new URI("http://localhost:8378")
        val bindRequest = new HttpRequest(HttpMethod.POST,bindUrl,List(QUEUE.name -> testQueueId.toString, LINK.name -> "<%s>; rel=\"wq\"".format(bindCallbackUrl.toASCIIString), ROUTING_KEY.name -> "a.*.c"))
        HttpClient.blockingCall(bindRequest,bindTimeout*1000) match {
          case None =>
            fail("Bind response timed out after %d seconds ".format(bindTimeout))
          case Some(response) if (response.status.code != HttpStatus.CREATED().code) =>
            fail("Bind response did not return expected 201 Created status: %s".format(response))
          case Some(response) =>
            wqLog.info("Bind successfully created")

        }


//--- Publish a message to the exchange
        val publishTimeout = 3
        val publishUrl = new URI("http://localhost:4667/exchange/test-%s/publish".format(testExchangeId.toString))
        wqLog.info("Body Should be MD5(%s)".format(util.Utils.md5(testMessageBody)))
        val publishHeaders = List(
          MESSAGE_ID.name -> testMessageId.toString,
          ROUTING_KEY.name -> "a.b.c",
          HttpHeader.CONTENT_LENGTH.name -> testMessageBody.length.toString
        )
        val publishRequest = new HttpRequest(HttpMethod.POST,publishUrl,publishHeaders, testMessageBody)
        wqLog.info("Sending request: %s".format(publishRequest.toString))

        HttpClient.blockingCall(publishRequest,publishTimeout*1000) match {
          case None =>
            fail("Publish response timed out after %d seconds ".format(publishTimeout))
          case Some(response) if (response.status.code != HttpStatus.ACCEPTED().code) =>
            fail("Publish response did not return expected 201 Created status: %s".format(response))
          case Some(response) =>
            wqLog.info("Publish successfully completed")
        }


//--- Check that delivery has happened
        lock.synchronized {
          lock.wait(2*1000)
        }
        Option(requests.get(testMessageId)) match {
          case None =>
            fail("expected to find message ID %s in the map of delivered messages, but did not".format(testMessageId.toString))
          case Some(delivery) if (md5(delivery.body) != md5(testMessageBody)) =>
            fail("expected the delivered message's body (%d bytes, MD5 %s) to match the test message body(%d bytes, MD5 %s): %s".format(
              delivery.body.length,
              md5(delivery.body),
              testMessageBody.length,
              md5(testMessageBody),
              delivery
            ))
          case Some(delivery) =>
            wqLog.info("Successfully delivered message %s".format(delivery))
        }
      }
      finally {
        channelGroup.close().awaitUninterruptibly()
        channelFactory.releaseExternalResources()
      }
    }



  }
}
