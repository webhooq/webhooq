package webhooq.http

import org.junit.runner.RunWith
import org.specs.runner.{JUnit, JUnitSuiteRunner}
import org.specs.Specification
import akka.actor.{Props, Actor}
import webhooq.Akka
import webhooq.logging.WebhooqLogger
import java.net.URI
import org.jboss.netty.channel._
import java.util.regex.Pattern
import webhooq.http.netty.HttpClient

//import org.jboss.netty.handler.codec.http.{HttpResponse, HttpRequest}

import org.jboss.netty.channel.group.{DefaultChannelGroup, ChannelGroup}


@RunWith(classOf[JUnitSuiteRunner])
class HttpClientSpec extends Specification with Akka with WebhooqLogger with JUnit /*with ScalaCheck*/ {
//  val HTML_PATTERN = Pattern.compile("<!doctype html><html.*<\\/html>")
  def actorSystemName:String = "test-webhooq"

  "HttpClient" should {
    "get a valid HTML response from http://www.google.com" in {
      val google = new URI("http://www.google.com/index.html")
      var callbackCalled = false
      val lock = new Object
      val callback = (response:HttpResponse) => {
        if (response.status.code >= 200 && response.status.code <= 299)  {
          val body = new String(response.body)
          wqLog.debug("Body: %s".format(body))
          val matched = body.startsWith("<!doctype html><html") && body.endsWith("</html>")
          wqLog.debug("Response body was HTML? %s".format(matched.toString))
          if (matched) {
            wqLog.info("Call to %s succeeded.".format(google.toASCIIString))
            callbackCalled = true
            lock.synchronized {
              lock.notifyAll()
            }
          } else {
            fail("no HTML body inresponse.")
          }

        }
        else {
          fail("Call to %s failed with a status code: %s.".format(google.toASCIIString,response.status.toString))
        }
      }
      //val ref = akkaSystem.actorOf(Props(new ResponseActor(callback)), name = "ResponseActor")
     // val channelGroup:ChannelGroup = new DefaultChannelGroup("http-client")
      HttpClient.call(
        new HttpRequest(HttpMethod.GET,google ),
        callback
      )

      lock.synchronized {
        lock.wait(3*1000)
      }

      if (!callbackCalled) {
        fail("Response callback was not invoked.")
      }
    }
  }
}
object HttpClientSpecMain {
  def main(args: Array[String]) {
    new HttpClientSpec().main(args)
  }
}

//class ResponseActor(callback: HttpClientResponseMessage => Unit) extends Actor with WebhooqLogger {
//  def receive = {
//    case Success => callback(Success)
//    case Canceled => callback(Canceled)
//    case Err(cause) => callback(Err(cause))
//  }
//}

//class ResponseHandler(val allChannels:ChannelGroup) extends SimpleChannelHandler  with WebhooqLogger {
//  override def channelOpen(ctx:ChannelHandlerContext, e:ChannelStateEvent ):Unit = {
//    wqLog.info("ResponseHandler.channelOpen")
//    allChannels.add(e.getChannel)
//  }
//
//  override def exceptionCaught(ctx:ChannelHandlerContext, e:ExceptionEvent):Unit = {
//    wqLog.info("ResponseHandler.exceptionCaught")
//    e.getCause.printStackTrace()
//    e.getChannel.close()
//  }
//
//  override def channelClosed(ctx:ChannelHandlerContext, e:ChannelStateEvent):Unit = {
//    wqLog.info("ResponseHandler.channelClosed")
//    super.channelClosed(ctx, e)
//  }
//
//  override def messageReceived(ctx:ChannelHandlerContext, e:MessageEvent):Unit = {
//    wqLog.info("ResponseHandler.messageReceived")
//    val response = e.getMessage.asInstanceOf[HttpResponse]
//    val content  = response.getContent
//    val size     = content.readableBytes()
//    val bytes    = new Array[Byte](size)
//    response.getContent.readBytes(bytes,0,size)
//    wqLog.info("body: "+new String(bytes))
//
//
//  }
//}