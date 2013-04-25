package webhooq

import org.junit.runner.RunWith
import org.specs.runner.{JUnit, JUnitSuiteRunner}
import org.specs.Specification
import webhooq.logging.WebhooqLogger
import webhooq.http.{HttpHeader, HttpMethod, SaveRequestByUuidInPathHttpServer, HttpStatus, HttpResponse, HttpRequest, HttpServer}

import java.util.UUID
import java.net.URI
import webhooq.model.{Direct, Fanout, Topic, ExchangeType, Type}
import webhooq.http.netty.{RequestHandler, HttpClient}

import scala.collection.JavaConverters.collectionAsScalaIterableConverter

/**
 */
@RunWith(classOf[JUnitSuiteRunner])
class TopicRoundTripSpec extends Specification with WebhooqLogger with JUnit {
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

      publish(exchange_A, "a.b.c.d", Map(RequestHandler.HEADERS.MESSAGE_ID -> message.toString), messageBody)

      Thread.sleep(1 * 1000)
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
      publish(exchange_A, "b.c.d.a", Map(RequestHandler.HEADERS.MESSAGE_ID -> message.toString), messageBody)

      Thread.sleep(1 * 1000)

      val requestKeys = collectionAsScalaIterableConverter(server.requests.keySet()).asScala
      if (requestKeys.size != 1) fail("did not get the expected delivery. dumping keys: [%s]".format(requestKeys.mkString(",")))
      if (requestKeys.head != queue_1) fail("did not receive delivery only to queue_1(%s) as expected, instead got %s.".format(queue_1.toString,requestKeys.head.toString))

      wqLog.info("received delivery only to queue_1 as expected")
    }


    "route a message with key 'a.b' only into queues 1, 3, and 5" in {
      server.requests.clear()

      val message = UUID.randomUUID()

      val messageBody = message.toString.getBytes("UTF-8")
      publish(exchange_A, "a.b", Map(RequestHandler.HEADERS.MESSAGE_ID -> message.toString), messageBody)

      Thread.sleep(1 * 1000)

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
      publish(exchange_A, "a.c.b.d", Map(RequestHandler.HEADERS.MESSAGE_ID -> message.toString), messageBody)

      Thread.sleep(1 * 1000)

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
      publish(exchange_A, "a.z.c.d", Map(RequestHandler.HEADERS.MESSAGE_ID -> message.toString), messageBody)

      Thread.sleep(1 * 1000)

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
      publish(exchange_A, "a.b.z.d", Map(RequestHandler.HEADERS.MESSAGE_ID -> message.toString), messageBody)

      Thread.sleep(1 * 1000)

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

  def declareExchange(exchange:UUID, exchangeType:ExchangeType) {
    val declareExchangeTimeout = 3
    val declareExchangeUrl = new URI("http://localhost:4667/exchange/%s?%s=%s".format(exchange.toString, Type.name, exchangeType.name))
    val declareExchangeRequest = new HttpRequest(HttpMethod.POST,declareExchangeUrl)
    HttpClient.blockingCall(declareExchangeRequest, declareExchangeTimeout * 1000) match {
      case None =>
        fail("Declare Exchange response timed out after %d seconds ".format(declareExchangeTimeout))
      case Some(response) if (response.status.code != HttpStatus.CREATED().code) =>
        fail("Declare Exchange response did not return expected 201 Created status: %s".format(response))
      case Some(response) =>
        if (wqLog.isInfoEnabled) wqLog.info("Declare Exchange '%s' successfully created".format(exchange.toString))
    }
  }

  def declareQueue(queue:UUID) {
    val declareQueueTimeout = 3
    val declareQueueUrl = new URI("http://localhost:4667/queue/%s".format(queue.toString))
    val declareQueueRequest = new HttpRequest(HttpMethod.POST,declareQueueUrl)
    HttpClient.blockingCall(declareQueueRequest,declareQueueTimeout * 1000) match {
      case None =>
        fail("Declare Queue response timed out after %d seconds ".format(declareQueueTimeout))
      case Some(response) if (response.status.code != HttpStatus.CREATED().code) =>
        fail("Declare Queue response did not return expected 201 Created status: %s".format(response))
      case Some(response) =>
        if (wqLog.isInfoEnabled) wqLog.info("Declare Queue '%s' successfully created".format(queue.toString))

    }
  }

  def bind(source:UUID, destination:Either[UUID, (UUID,URI)], routing_key:String) {
    val bindTimeout = 3
    val bindUrl = new URI("http://localhost:4667/exchange/%s/bind".format(source))
    val headers = destination match {
      case Left(id) =>
        List(
          RequestHandler.HEADERS.EXCHANGE -> id.toString,
          RequestHandler.HEADERS.ROUTING_KEY -> routing_key
        )
      case Right(t) =>
        List(
          RequestHandler.HEADERS.QUEUE -> t._1.toString,
          RequestHandler.HEADERS.LINK -> "<%s>; rel=\"wq\"".format(t._2.toASCIIString),
          RequestHandler.HEADERS.ROUTING_KEY -> routing_key
        )
    }
    val bindRequest = new HttpRequest(HttpMethod.POST,bindUrl,headers)
    HttpClient.blockingCall(bindRequest,bindTimeout*1000) match {
      case None =>
        fail("Bind response timed out after %d seconds ".format(bindTimeout))
      case Some(response) if (response.status.code != HttpStatus.ACCEPTED().code) =>
        fail("Bind response did not return expected 201 Created status: %s".format(response))
      case Some(response) =>
        wqLog.info("Bind successfully created")

    }
  }

  def publish(exchange:UUID, routing_key:String, headers:Map[String,String], message:Array[Byte]) = {
    val publishTimeout = 3
    val publishUrl = new URI("http://localhost:4667/exchange/%s/publish".format(exchange.toString))
    val publishHeaders = headers + (RequestHandler.HEADERS.ROUTING_KEY -> routing_key) + (HttpHeader.CONTENT_LENGTH.name -> message.length.toString)
    val publishRequest = new HttpRequest(HttpMethod.POST,publishUrl,publishHeaders.toList, message)
    wqLog.info("Sending request: %s".format(publishRequest.toString))

    HttpClient.blockingCall(publishRequest,publishTimeout * 1000) match {
      case None =>
        fail("Publish response timed out after %d seconds ".format(publishTimeout))
      case Some(response) if (response.status.code != HttpStatus.ACCEPTED().code) =>
        fail("Publish response did not return expected 201 Created status: %s".format(response))
      case Some(response) =>
        wqLog.info("Publish successfully completed")
    }
  }
}
