package webhooq.http.netty

import java.util.UUID
import java.nio.charset.Charset
import org.jboss.netty.channel.{ChannelFuture,ChannelFutureListener,ChannelHandlerContext,ChannelStateEvent,ExceptionEvent,MessageEvent,SimpleChannelHandler}
import org.jboss.netty.channel.group.ChannelGroup
import scala.collection.JavaConverters.collectionAsScalaIterableConverter
import webhooq.Schema
import webhooq.model._
import webhooq.model.dao._
import webhooq.logging.WebhooqLogger
import webhooq.model.dao.Message
import webhooq.model.dao.Incoming
import webhooq.http.netty.NettyHttpConverters._
import webhooq.http.{HttpHeader, HttpStatus, HttpResponse, HttpMethod, HttpRequest}

/**
 *
 *
 */
class RequestHandler ( val allChannels:ChannelGroup) extends SimpleChannelHandler with WebhooqLogger {
  import RequestHandler._

//---------------------------------------------------------------------------
// ChannelHandler functions
//---------------------------------------------------------------------------
  override def channelOpen(ctx:ChannelHandlerContext, e:ChannelStateEvent ): Unit = {
    allChannels.add(e.getChannel)
  }

  override def exceptionCaught(ctx:ChannelHandlerContext, e:ExceptionEvent):Unit = {
    wqLog.error("while receiving",e.getCause)
    e.getChannel.close
  }

  override def messageReceived(ctx:ChannelHandlerContext, e:MessageEvent):Unit = {
    val request:HttpRequest = e.getMessage.asInstanceOf[org.jboss.netty.handler.codec.http.HttpRequest]
    if (wqLog.isDebugEnabled) wqLog.debug("RequestHandler.messageReceived: %s".format(request))
//    /**
//     * the whole API is 4 URLs...
//     *   GET     /stats   ; none
//     *   POST    /bind    ; Required headers: x-wq-vhost, x-wq-exchange, x-wq-queue, x-wq-rkey, x-wq-link
//     *   DELETE  /bind    ; Required headers: x-wq-vhost, x-wq-exchange, x-wq-queue, x-wq-rkey
//     *   POST    /publish ; Required headers: x-wq-vhost, x-wq-exchange, x-wq-rkey
//     * and 6 headers...
//     *   x-wq-vhost    ; the name of a virtual-host, UTF-8 String, must not exceed 255 bytes and be URL-safe.
//     *   x-wq-exchange ; the name of an exchange;(direct|topic|fanout|match), UTF-8 String, must not exceed 255 bytes. Link-Value format
//     *   x-wq-queue    ; the name of a queue, UTF-8 String, must not exceed 255 bytes. Link-Value format
//     *   x-wq-rkey     ; the routing key to use. Follows AMQP's format, UTF-8 String, entire key must not exceed 255 bytes.
//     *   x-wq-link     ; the callback webhook. follows the rfc5988 Link header format. the URL with a rel of 'webhooq' will be used.
//     *   x-wq-msg-id   ; the UUID identifying a message. If sent, will be used, if not sent, a random id will be used.
//     */
    val response:org.jboss.netty.handler.codec.http.HttpResponse = (request.method, request.uri.getPath.split("/").map(_.trim).toList) match {
      case (HttpMethod.GET   , ("" :: "stats" :: Nil)    ) => processStats(request)

      case (HttpMethod.POST  , ("" :: "queue" :: queue_name :: Nil)    ) => processDeclareQueue(request, queue_name)
      case (HttpMethod.DELETE, ("" :: "queue" :: queue_name :: Nil)    ) => processDeleteQueue(request, queue_name)

      case (HttpMethod.POST  , ("" :: "exchange" :: exchange_name :: Nil) ) => processDeclareExchange(request,exchange_name)
      case (HttpMethod.DELETE, ("" :: "exchange" :: exchange_name :: Nil) ) => processDeleteExchange(request,exchange_name)
      case (HttpMethod.POST  , ("" :: "exchange" :: exchange_name :: "bind" :: Nil)) => processBind(request, exchange_name)
      case (HttpMethod.DELETE, ("" :: "exchange" :: exchange_name :: "bind" :: Nil)) => processUnbind(request, exchange_name)
      case (HttpMethod.POST  , ("" :: "exchange" :: exchange_name :: "publish" :: Nil)  ) => processPublish(request, exchange_name)

      case (m,u) =>
        val errmsg = "Unknown service %s for method %s".format(u, m.toString)
        RESPONSE.notFound(errmsg)
    }

    e.getChannel().write(response).addListener(new ChannelFutureListener() {
      def operationComplete(future:ChannelFuture ):Unit = {
        future.getChannel().close()
      }
    })
  }
//---------------------------------------------------------------------------


//---------------------------------------------------------------------------
// Response Handlers for Stats, Bind, Unbind, and Publish requests.
//---------------------------------------------------------------------------
  /**
   * replies with a JSON payload of system health statistics.
   */
  def processStats(request:HttpRequest): HttpResponse = {
    // TODO: Assemble stats into a JSON payload

    val body = "{}".getBytes(Charset.forName("UTF-8"))
    new HttpResponse(
      HttpStatus.CREATED(),
      List[(String,String)](
        HttpHeader.CONTENT_LENGTH.name ->  body.length.toString,
        HttpHeader.CONTENT_TYPE.name -> "application/json; encoding=utf-8"
      ),
      body
    )
  }

  /**
   * Creates an exchange
   * /exchange/:exchange-name
   *
   * required headers:
   *     Host used for multi-tenancy.
   * responds with:
   *   201 - If the exchange was created successfully.
   *   400 - If creating the exchange failed.
   */
  def processDeclareExchange(request: HttpRequest, exchange_name:String): HttpResponse = {

    def doProcessDeclareExchange(vhost:String, exchange:String, arguments: Map[String, String]): HttpResponse = {
      // ensure that we have an exchange type
      val direct = (Type.name -> Direct.name)
      val arrivedAt = (ArrivedAt.name -> System.currentTimeMillis().toString)
      val exchangeRef =  ExchangeRef(vhost,exchange)
      val isExchangeTypeValid = isValidExchangeType(arguments)
      val exchangeArgs = ((if (isExchangeTypeValid) arguments else arguments + direct) + arrivedAt).map { t => Argument(t._1, t._2)}
      Schema.tx {
        val doesExchangeExist = Schema.exchange_arguments.containsKey(exchangeRef)
        if (doesExchangeExist) {
          RESPONSE.badRequest("Exchange '%s' already exists.".format(exchange))
        } else {
          exchangeArgs.foreach{ arg =>
            if (wqLog.isDebugEnabled) wqLog.debug("Putting Exchange Argument %s => %s".format(arg.key,arg.value))
            Schema.exchange_arguments.put(exchangeRef,arg)
          }
          RESPONSE.created()
        }
      }
    }
    // AMQP arguments are equivalent to HTTP query parameters
    val arguments = request.queryParameters.map( t => (t._1,t._2.head)).toMap

    (parseVirtualHost(request), parseExchange(exchange_name)) match {
      case (NotFound, _)       => RESPONSE.badRequest("Host header missing from request. A Host header is needed to declare an exchange.")
      case (_, NotFound)       => RESPONSE.badRequest("Missing Exchange name, expected 'POST /exchange/:exchange_name' where :exchange_name is the name of the exchange to create.")
      case (Malformed(msg), _) => RESPONSE.badRequest("Host header failed to parse. A Host header is needed to declare an exchange. See rfc2616 section 14.23")
      case (_, Malformed(msg)) => RESPONSE.badRequest("Exchange name failed to parse, expected 'POST /exchange/:exchange_name' where :exchange_name can be any URL-safe string")
      case (Success(vhost), Success(exchange)) => doProcessDeclareExchange(vhost,exchange, arguments)
    }
  }

  /**
   * Deletes an exchange
   * required headers:
   *     Host
   *     x-wq-exchange
   * responds with:
   *   204 - If the exchange was deleted successfully.
   *   404 - If the exchange doesnt exist
   */
  def processDeleteExchange(request: HttpRequest, exchange_name:String): HttpResponse = {
    def doProcessDeleteExchange(vhost:String, exchange:String): HttpResponse = {
      val exchangeRef = ExchangeRef(vhost,exchange)
      Schema.tx {
        val doesExchangeExist = Schema.exchange_arguments.containsKey(exchangeRef)
        if (!doesExchangeExist) {
          RESPONSE.notFound("Exchange '%s' does not exist.".format(exchange))
        } else {
          Schema.exchange_arguments.remove(exchangeRef)
          RESPONSE.no_content()
//          new HttpResponse(HttpStatus.NO_CONTENT(),List[(String,String)](HttpHeader.CONTENT_LENGTH.name ->  0.toString))
        }
      }
    }

    (parseVirtualHost(request), parseExchange(exchange_name)) match {
      case (NotFound, _)       => RESPONSE.badRequest("Host header missing from request. A Host header is needed to delete an exchange.")
      case (_, NotFound)       => RESPONSE.badRequest("Missing Exchange name, expected 'DELETE /exchange/:exchange_name' where :exchange_name is the name of the exchange to delete.")
      case (Malformed(msg), _) => RESPONSE.badRequest("Host header failed to parse. A Host header is needed to delete an exchange. See rfc2616 section 14.23")
      case (_, Malformed(msg)) => RESPONSE.badRequest("Exchange name failed to parse, expected 'DELETE /exchange/:exchange_name' where :exchange_name can be any URL-safe string")
      case (Success(vhost), Success(exchange)) => doProcessDeleteExchange(vhost,exchange)
    }
  }

  /**
   * Creates a queue
   * required headers:
   *     Host
   *     x-wq-queue
   * responds with:
   *   201 - If the exchange was created successfully.
   *   400 - If creating the exchange failed.
   */
  def processDeclareQueue(request: HttpRequest, queue_name:String): HttpResponse = {
    def doProcessDeclareQueue(vhost:String, queue:String, arguments:Map[String, String]): HttpResponse = {
      val arrivedAt = (ArrivedAt.name -> System.currentTimeMillis().toString)
      val queueRef = QueueRef(vhost,queue_name)
      val queueArgs = (arguments + arrivedAt).map { t => Argument(t._1, t._2)}
      Schema.tx {
        val doesQueueExist = Schema.queue_arguments.containsKey(queueRef)
        if (doesQueueExist) {
          RESPONSE.badRequest("Queue '%s' already exists.".format(queue_name))
        } else {
          queueArgs.foreach{  arg =>
            if (wqLog.isDebugEnabled) wqLog.debug("Putting Exchange Argument %s => %s".format(arg.key,arg.value))
            Schema.queue_arguments.put(queueRef,arg)
          }
          RESPONSE.created()
//          new HttpResponse(HttpStatus.CREATED(),List[(String,String)](HttpHeader.CONTENT_LENGTH.name ->  0.toString))
        }
      }
    }

    //TODO Parse URL params into an args map
    val arguments = Map.empty[String, String]

    (parseVirtualHost(request), parseQueue(queue_name)) match {
      case (NotFound, _)       => RESPONSE.badRequest("Host header missing from request. A Host header is needed to declare a queue.")
      case (_, NotFound)       => RESPONSE.badRequest("Missing Queue name, expected 'POST /queue/:queue_name' where :queue_name is the name of the queue to create.")
      case (Malformed(msg), _) => RESPONSE.badRequest("Host header failed to parse. A Host header is needed to declare a queue. See rfc2616 section 14.23")
      case (_, Malformed(msg)) => RESPONSE.badRequest("Queue name failed to parse, expected 'POST /queue/:queue_name' where :queue_name can be any URL-safe string")
      case (Success(vhost), Success(queue)) => doProcessDeclareQueue(vhost,queue, arguments)
    }
  }

  /**
   * Deletes a queue
   * required headers:
   *     Host
   *     x-wq-queue
   * responds with:
   *   204 - If the exchange was deleted successfully.
   *   404 - If deleting the exchange failed.
   */
  def processDeleteQueue(request: HttpRequest, queue_name:String): HttpResponse = {
    def doProcessDeleteQueue(vhost:String, queue:String): HttpResponse = {
      val queueRef = QueueRef(vhost,queue)
      Schema.tx {
        val doesQueueExist = Schema.queue_arguments.containsKey(queueRef)
        if (!doesQueueExist) {
          RESPONSE.notFound("Queue '%s' does not exist.".format(queue))
        } else {
          Schema.queue_arguments.remove(queueRef)
          RESPONSE.no_content()
        }
      }
    }

    (parseVirtualHost(request), parseQueue(queue_name)) match {
      case (NotFound, _)       => RESPONSE.badRequest("Host header missing from request. A Host header is needed to delete a queue.")
      case (_, NotFound)       => RESPONSE.badRequest("Missing Queue name, expected 'DELETE /queue/:queue_name' where :queue_name is the name of the queue to delete.")
      case (Malformed(msg), _) => RESPONSE.badRequest("Host header failed to parse. A Host header is needed to delete a queue. See rfc2616 section 14.23.")
      case (_, Malformed(msg)) => RESPONSE.badRequest("Queue name failed to parse, expected 'DELETE /queue/:queue_name' where :queue_name can be any URL-safe string.")
      case (Success(vhost), Success(exchange)) => doProcessDeleteQueue(vhost,exchange)
    }
  }

  /**
   * unbinds a webhook
   */
  def processUnbind(request:HttpRequest, exchange_name:String): HttpResponse = {
        // TODO: remove the ( (vhost.exchange), (queue,routing_key, link) ) to the hazelcast bindings map.
        RESPONSE.accepted()
  }


  /**
   * Binds a webhook to a queue and anexchange, based on a routing key.
   */
  def processBind(request:HttpRequest, exchange_name:String): HttpResponse = {

    def doBind(vhost:String, exchange:String, bind:Either[String,(String,Link)], routing_key:String):HttpResponse = {
      val exchangeRef = ExchangeRef(vhost,exchange)
      bind match {
        // bind an exchange to the exchange.
        case Left(destExchange)  =>
          val destExchangeRef = ExchangeRef(vhost,destExchange)
          Schema.tx {
            if (Schema.bindings.containsKey(exchangeRef)) {
              // only add  binding to the exchange, if the biding isn't set.
              val exchangeBindings = collectionAsScalaIterableConverter(Schema.bindings.get(exchangeRef)).asScala

              if (exchangeBindings.filter(exchangeBinding => exchangeBinding.isExchange && exchangeBinding.binding.left.get.exchangeRef == destExchangeRef && exchangeBinding.getRoutingKey() == routing_key ).size < 1) {
                Schema.bindings.put(exchangeRef,new Binding(Left(new ExchangeBinding(destExchangeRef,routing_key))))
              }

            } else {
              Schema.bindings.put(exchangeRef,new Binding(Left(new ExchangeBinding(destExchangeRef,routing_key))))
            }
            RESPONSE.accepted()
          }

        // bind a queue to the exchange.
        case Right( (queue,link) ) =>
          val queueRef = QueueRef(vhost,queue)
          Schema.tx {
            if (Schema.bindings.containsKey(exchangeRef)) {
              // only add  binding to the exchange, if the biding isn't set.
              val exchangeBindings = collectionAsScalaIterableConverter(Schema.bindings.get(exchangeRef)).asScala

              if (exchangeBindings.filter(exchangeBinding => exchangeBinding.isQueue && exchangeBinding.binding.right.get.queueRef == queueRef && exchangeBinding.getRoutingKey() == routing_key ).size < 1) {
                Schema.bindings.put(exchangeRef,new Binding(Right(new QueueBinding(queueRef,routing_key,link))))
              }

            } else {
              Schema.bindings.put(exchangeRef,new Binding(Right(new QueueBinding(queueRef,routing_key,link))))
            }
          }
          RESPONSE.accepted()
      }

    }

    (parseVirtualHost(request), parseExchange(exchange_name), parseBind(request), parseRoutingKey(request)) match {
      case (NotFound, _, _, _)       => RESPONSE.badRequest("Host header missing from request. A Host header is needed to crate a binding.")
      case (_, NotFound, _, _)       => RESPONSE.badRequest("Missing Exchange name, expected 'POST /exchange/:exchange_name' where :exchange_name is the name of the exchange to create.")
      case (_, _, NotFound, _)       => RESPONSE.badRequest("This should never happen!")
      case (_, _, _, NotFound)       => RESPONSE.badRequest("Routing key header '%s' is required to crate a binding".format(HEADERS.ROUTING_KEY))
      case (Malformed(msg), _, _, _) => RESPONSE.badRequest("Host header failed to parse. A Host header is needed to crate a binding.. See rfc2616 section 14.23.")
      case (_, Malformed(msg), _, _) => RESPONSE.badRequest("Missing Exchange name, expected 'POST /exchange/:exchange_name' where :exchange_name is the name of the exchange to create.")
      case (_, _, Malformed(msg), _) => RESPONSE.badRequest(msg)
      case (_, _, _, Malformed(msg)) => RESPONSE.badRequest(msg)
//      case (_, _, _, _, Malformed(msg)) => RESPONSE.badRequest(malformedHeader(HEADERS.LINK,msg))
      case (Success(vhost), Success(exchange), Success(bind), Success(routing_key)) =>
        doBind(vhost,exchange,bind,routing_key)
    }
  }

  /**
   * accepts an HTTP body as a message, writes it to the message storage and the incoming queue.
   * example:
   * curl -v -X PUBLISH \
   *   -H "x-wq-vhost:myAppName" \
   *   -H "x-wq-exchange:myExchangeName;direct" \
   *   -H "x-wq-queue:myQueueName" \
   *   -H "x-wq-rkey:#" \
   *   -H "x-wq-link:http://somecallback/here" \
   *   http://localhost:8080
   */
  def processPublish(request:HttpRequest, exchange_name:String): HttpResponse = {
    def doPublish(message_id:UUID, vhost:String, exchange:String, routing_key:String): HttpResponse = {
      val messageRef =  MessageRef(vhost,message_id)
      val content = request.getContent
      val message = new Message(new Array[Byte](content.readableBytes()))
      content.readBytes(message.body,0,content.readableBytes())

      if (wqLog.isInfoEnabled) wqLog.info("Writing %s as %s to the messages map.".format(message.toString, message_id.toString))
      Schema.messages.put(messageRef, message)

      val arrivedAt = (ArrivedAt.name -> System.currentTimeMillis().toString)

      val headers = (collectionAsScalaIterableConverter(request.getHeaders).asScala.foldLeft(scala.collection.mutable.Map.empty[String,String]) { (acc,e) =>
        if (e.getKey == HttpHeader.ACCEPT.name) acc
        else acc + (e.getKey -> e.getValue)
      }) + arrivedAt

      val exchangeRef = ExchangeRef(vhost,exchange)

      val incoming = new Incoming(exchangeRef, routing_key, headers.toMap, messageRef)

      if (wqLog.isInfoEnabled) wqLog.info("Writing Incoming [%s] to the incoming queue.".format(incoming.toString))
      Schema.incoming.put(incoming)

      RESPONSE.accepted()
    }

    (parseMessageId(request), parseVirtualHost(request), parseExchange(exchange_name),parseRoutingKey(request)) match {
      case (Malformed(_), _, _, _) =>
        RESPONSE.badRequest("The '%s' header contained a value that failed to parse into a valid java.util.UUID instance".format(HEADERS.MESSAGE_ID))
      case (Success(message_id), _, _, _) if (Schema.messages.containsKey(message_id)) =>
        RESPONSE.badRequest("The '%s' header contains a UUID value that is already in use. Please use a different".format(HEADERS.MESSAGE_ID))
      case (_, NotFound, _, _) => RESPONSE.badRequest("Host header missing from request. A Host header is needed to publish a message.")
      case (_, _, NotFound, _) => RESPONSE.badRequest("Missing Exchange name, expected 'POST /exchange/:exchange_name/publish' where :exchange_name is the name of the exchange to publish the message to.")
      case (_, _, _, NotFound) => RESPONSE.badRequest("Routing key header '%s' is required to publish a message".format(HEADERS.ROUTING_KEY))
      case (_, Malformed(msg), _, _) => RESPONSE.badRequest("Host header failed to parse. A Host header is needed to publish a message. See rfc2616 section 14.23.")
      case (_, _, Malformed(msg), _) => RESPONSE.badRequest("Exchange name failed to parse, expected 'POST /exchange/:exchange_name/publish' where :exchange_name can be any URL-safe string")
      case (_, _, _, Malformed(msg)) => RESPONSE.badRequest(msg)
      case (NotFound, Success(vhost), Success(exchange), Success(routing_key)) =>
        val message_id = UUID.randomUUID()
        doPublish(message_id,vhost,exchange,routing_key)
      case (Success(message_id), Success(vhost), Success(exchange), Success(routing_key)) =>
        doPublish(message_id,vhost,exchange,routing_key)
    }
  }
//---------------------------------------------------------------------------


}

/**
 *
 */
object RequestHandler {
  abstract class ParseHeaderResult[+A]
  final case object NotFound                 extends ParseHeaderResult[Nothing]
  final case class  Malformed(message:String) extends ParseHeaderResult[Nothing]
  final case class  Success[A](value:A)       extends ParseHeaderResult[A]

  /**
   * a collection of HTTP headers we use.
   */
  object HEADERS {
    val HOST = HttpHeader.HOST.name
    val EXCHANGE     = "x-wq-exchange"    // exchange_name
    val QUEUE        = "x-wq-queue"       // queue_name
    val ROUTING_KEY  = "x-wq-routing-key" // an AMQP routing key
    val LINK         = "x-wq-link"        // an RFC-5988 Link Header value
    val MESSAGE_ID   = "x-wq-msg-id"      // UUID
 //   val ARGS_HEADERS = "x-wq-arg-headers" // Header1;Header2;Header3 -- Headers must also be included in bind
    //    val ARRIVED_AT   = "x-wq-arrived-at"  // the time this request arrived at
  }

  /**
   * a collection of error handeling functions.
   */
  object RESPONSE {
    def malformedHeader(header: String, method: String, message: String): String ={
      "The '%s' header, required to %s, appears to be malformed: %s".format(header,method,message)
    }

    def missingRequestHeader(header:String, method:String):String = {
      "The '%s' header is required to %s, however it was not found among the request headers.".format(header,method)
    }

    def badRequest (message:String):HttpResponse = {
      new HttpResponse(HttpStatus.BAD_REQUEST(message),List[(String,String)](HttpHeader.CONTENT_LENGTH.name ->  0.toString))
    }
    def notFound (message:String):HttpResponse = {
      new HttpResponse(HttpStatus.NOT_FOUND(message),List[(String,String)](HttpHeader.CONTENT_LENGTH.name ->  0.toString))
    }

    def no_content ():HttpResponse = {
      new HttpResponse(HttpStatus.NO_CONTENT(),List[(String,String)](HttpHeader.CONTENT_LENGTH.name ->  0.toString))
    }
    def created ():HttpResponse = {
      new HttpResponse(HttpStatus.CREATED(),List[(String,String)](HttpHeader.CONTENT_LENGTH.name ->  0.toString))
    }
    def ok (headers:List[(String,String)] = List.empty[(String,String)], body:Array[Byte] = Array.empty[Byte]):HttpResponse = {
      new HttpResponse(HttpStatus.OK(),headers,body)
    }
    def accepted ():HttpResponse = {
      new HttpResponse(HttpStatus.ACCEPTED(),List[(String,String)](HttpHeader.CONTENT_LENGTH.name ->  0.toString))
    }

  }
  //---------------------------------------------------------------------------
  /**
   *  if a message id was sent, it must be a valid UUID.
   *  if no message id is given, generate a random one, if one is given, parse it into an instance of UUID
   */
  def parseMessageId(request:HttpRequest):ParseHeaderResult[UUID] =
    request.getHeader(HEADERS.MESSAGE_ID) match {
      case Some(s) => Option(UUID.fromString(s)) match {
        case Some(uuid) => Success(uuid)
        case None       => Malformed("Malformed UUID")
      }
      case None    => NotFound
    }

  // TODO: Make Routing Keys parser
  def parseRoutingKey(request:HttpRequest):ParseHeaderResult[String] = //parseStringHeaderValue(request, "x-wq-rkey")
    request.getHeader(HEADERS.ROUTING_KEY) match {
      case Some(rkey) => Success(rkey)
      case None       => NotFound
    }

  /**
    */
  def parseVirtualHost(request:HttpRequest):ParseHeaderResult[String] =
    request.getHeader(HEADERS.HOST) match {
      case Some(vhost) => Success(vhost)
      case None        => NotFound
    }

  /**
    */
  val SAFE_URL_NAME_PATTERN = java.util.regex.Pattern.compile("^[\\w\\._\\-]+$")
  def parseExchange (input:String):ParseHeaderResult[String] = input match {
    case null => NotFound
    case s if !SAFE_URL_NAME_PATTERN.matcher(s).matches() => Malformed("invalid exchange name")
    case s => Success(s)
  }

  def parseExchangeHeader() (request:HttpRequest):ParseHeaderResult[String] =
    request.getHeader(HEADERS.EXCHANGE).map(parseExchange(_)).getOrElse(NotFound)


  /**
    */
  def parseQueue (input:String):ParseHeaderResult[String] = input match {
    case null => NotFound
    case s if !SAFE_URL_NAME_PATTERN.matcher(s).matches() => Malformed("invalid queue name")
    case s => Success(s)
  }

  def parseQueueHeader() (request:HttpRequest):ParseHeaderResult[String] =
    request.getHeader(HEADERS.QUEUE).map(parseExchange(_)).getOrElse(NotFound)

  /**
    */
  def parseLink(request:HttpRequest):ParseHeaderResult[Link] =
    request.getHeader(HEADERS.LINK) match {
      case None => NotFound
      case Some(exchangeValue) =>  LinkValueParser.parse(exchangeValue) match {
        case Left(error)       =>  Malformed(error)
        case Right(Nil)        =>  Malformed("empty list") // ???
        case Right(header)     =>  Success( Link(header) )
      }
    }

  /**
    */
  def parseBind(request:HttpRequest):ParseHeaderResult[Either[String,(String,Link)]] = {
    (request.getHeader(HEADERS.EXCHANGE), request.getHeader(HEADERS.QUEUE), request.getHeader(HEADERS.LINK)) match {
      case (None          , None       , _         ) => Malformed("A bind requires that either an '%s' header or a '%s' be defined.".format(HEADERS.EXCHANGE, HEADERS.QUEUE))
      case (None          , Some(_)    , None      ) => Malformed("A bind to a queue requires both a '%s' header and a '%s' header be defined.".format(HEADERS.QUEUE, HEADERS.LINK))
      case (None          , Some(queue), Some(_)   ) if !SAFE_URL_NAME_PATTERN.matcher(queue).matches() => Malformed("Header '%s' did not contain a valid queue name.".format(HEADERS.QUEUE))
      case (Some(exchange), None       , None      ) if !SAFE_URL_NAME_PATTERN.matcher(exchange).matches() => Malformed("Header '%s' did not contain a valid exchange name.".format(HEADERS.EXCHANGE))
      case (None          , Some(queue), Some(link)) =>  LinkValueParser.parse(link) match {
        case Left(error)       =>  Malformed(error)
        case Right(Nil)        =>  Malformed("Header '%s' contained an empty link list".format(HEADERS.LINK))
        case Right(header)     =>  Success(Right(queue -> Link(header)))
      }
      case (Some(exchange), None, None)  => Success(Left(exchange))

    }
  }

  //---------------------------------------------------------------------------

  //---------------------------------------------------------------------------
  def isValidExchangeType (key:String, value:String): Boolean = {
    Type.name == key && ExchangeType.parse(value).isDefined
  }
  def isValidExchangeType (arguments: Map[String, String]):Boolean = {
    arguments.find(t => isValidExchangeType(t._1,t._2)).isDefined
  }
  //---------------------------------------------------------------------------
}