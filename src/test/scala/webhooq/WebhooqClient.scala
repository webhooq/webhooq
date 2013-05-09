package webhooq

import java.util.UUID
import webhooq.model.{Type, ExchangeType}
import java.net.URI
import webhooq.http.{HttpHeader, HttpStatus, HttpMethod, HttpRequest}
import webhooq.http.netty.HttpClient
import webhooq.http.WebhooqHeader.{LINK, QUEUE, ROUTING_KEY, EXCHANGE}
import webhooq.logging.WebhooqLogger


trait WebhooqClient extends WebhooqLogger {
  def fail(m: String):Unit

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
          EXCHANGE.name -> id.toString,
          ROUTING_KEY.name -> routing_key
        )
      case Right(t) =>
        List(
          QUEUE.name -> t._1.toString,
          LINK.name -> "<%s>; rel=\"wq\"".format(t._2.toASCIIString),
          ROUTING_KEY.name -> routing_key
        )
    }
    val bindRequest = new HttpRequest(HttpMethod.POST,bindUrl,headers)
    HttpClient.blockingCall(bindRequest,bindTimeout*1000) match {
      case None =>
        fail("Bind response timed out after %d seconds ".format(bindTimeout))
      case Some(response) if (response.status.code != HttpStatus.CREATED().code) =>
        fail("Bind response did not return expected 201 Created status: %s".format(response))
      case Some(response) =>
        wqLog.info("Bind successfully created")

    }
  }

  def publish(exchange:UUID, routing_key:String, headers:Map[String,String], message:Array[Byte]) = {
    val publishTimeout = 3
    val publishUrl = new URI("http://localhost:4667/exchange/%s/publish".format(exchange.toString))
    val publishHeaders = headers + (ROUTING_KEY.name -> routing_key) + (HttpHeader.CONTENT_LENGTH.name -> message.length.toString)
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
