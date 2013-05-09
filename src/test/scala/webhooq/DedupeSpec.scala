package webhooq

import org.junit.runner.RunWith
import org.specs.runner.{JUnit, JUnitSuiteRunner}
import org.specs.Specification
import webhooq.logging.WebhooqLogger
import webhooq.http.RequestMultiMapHttpServer
import java.util.UUID
import webhooq.model.Fanout
import java.net.URI
import webhooq.http.WebhooqHeader.MESSAGE_ID


@RunWith(classOf[JUnitSuiteRunner])
class DedupeSpec extends Specification with JUnit with WebhooqClient with WebhooqLogger  {
  val CALLBACK_SERVER_PORT = 6676

  //--- Start a webhooq server on port 4667 ---
  System.setProperty("netty.port", 4667.toString)
  Main.main(Array.empty[String])
  val server = new RequestMultiMapHttpServer("dedup-test-http-server",10,CALLBACK_SERVER_PORT)
  val exchange = UUID.randomUUID()

  val queue_1 = UUID.randomUUID()
  val queue_2 = UUID.randomUUID()

  declareExchange(exchange, Fanout)

  declareQueue(queue_1)
  declareQueue(queue_2)

  val callbackURL = "http://localhost:%d/%s"
  val emptyRoute  = ""

  bind(exchange, Right(queue_1 -> new URI(callbackURL.format(CALLBACK_SERVER_PORT, queue_1.toString))), emptyRoute)
  bind(exchange, Right(queue_2 -> new URI(callbackURL.format(CALLBACK_SERVER_PORT, queue_2.toString))), emptyRoute)

  //---------------------------------------------------------------------
  "Messages sent twice" should {

    "Only be delivered once" in {
      server.requests.clear

      val message_1 = UUID.randomUUID()
      val message_2 = UUID.randomUUID()

      val message_1_body = message_1.toString.getBytes("UTF-8")
      val message_2_body = message_2.toString.getBytes("UTF-8")


      // publish message_1 twice, the second publish should NOT be delivered
      publish(exchange, emptyRoute, Map(MESSAGE_ID.name -> message_1.toString), message_1_body)
      publish(exchange, emptyRoute, Map(MESSAGE_ID.name -> message_1.toString), message_1_body)

      // publish message_2 once
      publish(exchange, emptyRoute, Map(MESSAGE_ID.name -> message_2.toString), message_2_body)

      // Wait for the deliveries
      Thread.sleep(1 * 1000)

      server.requests.valueIterator(queue_1).foldLeft(0) { (i,s) =>
        wqLog.info("queue_1(%s) value[%d]: %s".format(queue_1.toString,i,s))
        i+1
      }
      server.requests.valueIterator(queue_2).foldLeft(0) { (i,s) =>
        wqLog.info("queue_2(%s) value[%d]: %s".format(queue_2.toString,i,s))
        i+1
      }

      // extract the captured requests from our testing callback http server. The Webhooq server should have sent a total of two messages, message_1 once and message_2 once, to each queue.
      val (reqs_q1, reqs_q2) = (server.requests.valueIterator(queue_1).toList, server.requests.valueIterator(queue_2).toList)

      // Despite being sent twice, queue_q should only have been sent the message once.
      if (reqs_q1.size != 2) fail("Expected 2 deliveries to queue_1(%s) found %d.".format(queue_1.toString, reqs_q1.size))

      // As a control, queue_2 should have received its message once also.
      if (reqs_q2.size != 2) fail("Expected 2 deliveries to queue_2(%s) found %d.".format(queue_2.toString, reqs_q2.size))

      val (q1_req1, q1_req2) = (reqs_q1(0),reqs_q1(1))
      val (q2_req1, q2_req2) = (reqs_q2(0),reqs_q2(1))


      (q1_req1.headers.find{case (k,v) => k == MESSAGE_ID.name}, q1_req2.headers.find{case (k,v) => k == MESSAGE_ID.name}) match {
        case (None,_) => fail("Expected '%s' header in queue_1(%s)'s first  request:%s".format(MESSAGE_ID.name,queue_1.toString, q1_req1.toString))
        case (_,None) => fail("Expected '%s' header in queue_1(%s)'s second request:%s".format(MESSAGE_ID.name,queue_1.toString, q1_req2.toString))
        case (Some(foundId1),Some(foundId2)) =>
          val found = List[String](foundId1._2,foundId2._2)
          found.mustContain(message_1.toString)
          found.mustContain(message_2.toString)
      }
    }

  }
}
