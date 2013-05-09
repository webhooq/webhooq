package webhooq

import org.junit.runner.RunWith
import org.specs.runner.{JUnit, JUnitSuiteRunner}
import org.specs.Specification
import webhooq.logging.WebhooqLogger
import webhooq.http.{HttpHeader, HttpMethod, SaveRequestByUuidInPathHttpServer, HttpStatus, HttpRequest}

import java.util.UUID
import java.net.URI
import webhooq.model.{Direct, Fanout, Topic, ExchangeType, Type}
import webhooq.http.netty.{RequestHandler, HttpClient}

import webhooq.http.WebhooqHeader._

import scala.collection.JavaConverters.collectionAsScalaIterableConverter

/**
 */
@RunWith(classOf[JUnitSuiteRunner])
class TopicRoundTripSpec extends Specification with JUnit with WebhooqClient with WebhooqLogger {
  val CALLBACK_SERVER_PORT = 6666

  //--- Start a webhooq server on port 4667 ---
  System.setProperty("netty.port", 4667.toString)
  Main.main(Array.empty[String])
  val server = new SaveRequestByUuidInPathHttpServer("topic-routing-test-http-server",CALLBACK_SERVER_PORT)
  val exchange_A = UUID.randomUUID()
  val exchange_B = UUID.randomUUID()
  val exchange_C = UUID.randomUUID()
  val exchange_D = UUID.randomUUID()
  val queue_1 = UUID.randomUUID()
  val queue_2 = UUID.randomUUID()
  val queue_3 = UUID.randomUUID()
  val queue_4 = UUID.randomUUID()
  val queue_5 = UUID.randomUUID()
  val queue_6 = UUID.randomUUID()
  val queue_7 = UUID.randomUUID()

  val exchange_B_route = "a.#"
  val queue_1_route = "#"
  val queue_2_route = "a.*.c.d"
  val queue_4_route = "a.b.c.d"
  val queue_5_route = "#"
  val queue_6_route = "a.*.*.d"
  val queue_7_route = "*.b.*.d"

  declareExchange(exchange_A, Topic)
  declareExchange(exchange_B, Fanout)
  declareExchange(exchange_C, Topic)
  declareExchange(exchange_D, Direct)

  declareQueue(queue_1)
  declareQueue(queue_2)
  declareQueue(queue_3)
  declareQueue(queue_4)
  declareQueue(queue_5)
  declareQueue(queue_6)
  declareQueue(queue_7)

  val callbackURL = "http://localhost:%d/%s"
  val emptyRoute  = ""

  bind(exchange_A, Left(exchange_B), exchange_B_route)
  bind(exchange_A, Right(queue_1 -> new URI(callbackURL.format(CALLBACK_SERVER_PORT, queue_1.toString))), queue_1_route)
  bind(exchange_A, Right(queue_2 -> new URI(callbackURL.format(CALLBACK_SERVER_PORT, queue_2.toString))), queue_2_route)

  bind(exchange_B, Left(exchange_C), emptyRoute)
  bind(exchange_B, Left(exchange_D), emptyRoute)
  bind(exchange_B, Right(queue_3 -> new URI(callbackURL.format(CALLBACK_SERVER_PORT, queue_3.toString))), emptyRoute)

  bind(exchange_C, Right(queue_5 -> new URI(callbackURL.format(CALLBACK_SERVER_PORT, queue_5.toString))), queue_5_route)
  bind(exchange_C, Right(queue_6 -> new URI(callbackURL.format(CALLBACK_SERVER_PORT, queue_6.toString))), queue_6_route)
  bind(exchange_C, Right(queue_7 -> new URI(callbackURL.format(CALLBACK_SERVER_PORT, queue_7.toString))), queue_7_route)

  bind(exchange_D, Right(queue_4 -> new URI(callbackURL.format(CALLBACK_SERVER_PORT, queue_4.toString))), queue_4_route)

//---------------------------------------------------------------------
  "A Topic exchange" should {

    "route a message with key 'a.b.c.d' into every queue" in {
      server.requests.clear()

      val message = UUID.randomUUID()
      val messageBody = message.toString.getBytes("UTF-8")

      publish(exchange_A, "a.b.c.d", Map(MESSAGE_ID.name -> message.toString), messageBody)

      Thread.sleep(2 * 1000)
      val requestKeys = collectionAsScalaIterableConverter(server.requests.keySet()).asScala
      if (requestKeys.size != 7) fail("did not get the expected delivery. dumping keys: [%s]".format(requestKeys.mkString(",")))
      requestKeys.mustContain(queue_1)
      requestKeys.mustContain(queue_2)
      requestKeys.mustContain(queue_3)
      requestKeys.mustContain(queue_4)
      requestKeys.mustContain(queue_5)
      requestKeys.mustContain(queue_6)
      requestKeys.mustContain(queue_7)
      wqLog.info("received deliveries in all queues, as expected")

    }


    "route a message with key 'b.c.d.a' only into queue_1" in {
      server.requests.clear()

      val message = UUID.randomUUID()

      val messageBody = message.toString.getBytes("UTF-8")
      publish(exchange_A, "b.c.d.a", Map(MESSAGE_ID.name -> message.toString), messageBody)

      Thread.sleep(2 * 1000)

      val requestKeys = collectionAsScalaIterableConverter(server.requests.keySet()).asScala
      if (requestKeys.size != 1) fail("did not get the expected delivery. dumping keys: [%s]".format(requestKeys.mkString(",")))
      if (requestKeys.head != queue_1) fail("did not receive delivery only to queue_1(%s) as expected, instead got %s.".format(queue_1.toString,requestKeys.head.toString))

      wqLog.info("received delivery only to queue_1 as expected")
    }


    "route a message with key 'a.b' only into queues 1, 3, and 5" in {
      server.requests.clear()

      val message = UUID.randomUUID()

      val messageBody = message.toString.getBytes("UTF-8")
      publish(exchange_A, "a.b", Map(MESSAGE_ID.name -> message.toString), messageBody)

      Thread.sleep(2 * 1000)

      val requestKeys = collectionAsScalaIterableConverter(server.requests.keySet()).asScala
      if (requestKeys.size != 3) fail("did not get the expected delivery. dumping keys: [%s]".format(requestKeys.mkString(",")))
      requestKeys.mustContain(queue_1) //!= queue_1) fail("did not receive delivery only to queue_1(%s) as expected, instead got %s.".format(queue_1.toString,requestKeys.head.toString))
      requestKeys.mustContain(queue_3)
      requestKeys.mustContain(queue_5)
      wqLog.info("received delivery only to queues 1, 3, and 5 as expected")
    }


    "route a message with key 'a.c.b.d' only into queues 1, 3, 5 and 6" in {
      server.requests.clear()

      val message = UUID.randomUUID()

      val messageBody = message.toString.getBytes("UTF-8")
      publish(exchange_A, "a.c.b.d", Map(MESSAGE_ID.name -> message.toString), messageBody)

      Thread.sleep(2 * 1000)

      val requestKeys = collectionAsScalaIterableConverter(server.requests.keySet()).asScala
      if (requestKeys.size != 4) fail("did not get the expected delivery. dumping keys: [%s]".format(requestKeys.mkString(",")))
      requestKeys.mustContain(queue_1)
      requestKeys.mustContain(queue_3)
      requestKeys.mustContain(queue_5)
      requestKeys.mustContain(queue_6)
      wqLog.info("received delivery only to queues 1, 3, 5, and 6 as expected")
    }


    "route a message with key 'a.z.c.d' only into queues 1, 2, 3, 5 and 6" in {
      server.requests.clear()

      val message = UUID.randomUUID()

      val messageBody = message.toString.getBytes("UTF-8")
      publish(exchange_A, "a.z.c.d", Map(MESSAGE_ID.name -> message.toString), messageBody)

      Thread.sleep(2 * 1000)

      val requestKeys = collectionAsScalaIterableConverter(server.requests.keySet()).asScala
      if (requestKeys.size != 5) fail("did not get the expected delivery. dumping keys: [%s]".format(requestKeys.mkString(",")))
      requestKeys.mustContain(queue_1)
      requestKeys.mustContain(queue_2)
      requestKeys.mustContain(queue_3)
      requestKeys.mustContain(queue_5)
      requestKeys.mustContain(queue_6)
      wqLog.info("received delivery only to queues 1, 2, 3, 5, and 6 as expected")
    }


    "route a message with key 'a.b.z.d' only into queues 1, 3, 5, 6 and 7" in {
      server.requests.clear()

      val message = UUID.randomUUID()

      val messageBody = message.toString.getBytes("UTF-8")
      publish(exchange_A, "a.b.z.d", Map(MESSAGE_ID.name -> message.toString), messageBody)

      Thread.sleep(2 * 1000)

      val requestKeys = collectionAsScalaIterableConverter(server.requests.keySet()).asScala
      if (requestKeys.size != 5) fail("did not get the expected delivery. dumping keys: [%s]".format(requestKeys.mkString(",")))
      requestKeys.mustContain(queue_1)
      requestKeys.mustContain(queue_3)
      requestKeys.mustContain(queue_5)
      requestKeys.mustContain(queue_6)
      requestKeys.mustContain(queue_7)
      wqLog.info("received delivery only to queues 1, 3, 5, 6 and 7 as expected")
    }


  }
//---------------------------------------------------------------------


}
