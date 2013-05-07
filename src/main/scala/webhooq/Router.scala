package webhooq

import akka.actor.{Props, ActorRef, ActorContext, Actor}
import event.HazelcastQueueLister.QueueMessage
import webhooq.model.dao.{Delivery, DeliveryRef, ExchangeRef, Outgoing, Incoming}
import akka.event.Logging
import scala.collection.JavaConverters.collectionAsScalaIterableConverter
import model._
import webhooq.logging.WebhooqLogger
import webhooq.event.HazelcastQueueLister.QueueMessage
import java.util.UUID
import scala.collection.mutable.ListBuffer


class Router() extends Actor with WebhooqLogger {

  //val akkaLog = Logging(context.system, this)

  def receive = {
    case  QueueMessage(incoming:Incoming) =>
      val outgoing = processExchange(incoming.exchange,ListBuffer.empty[ExchangeRef],incoming,ListBuffer.empty[Outgoing])

      val placeholderDelivery = Delivery(System.currentTimeMillis(),-1)
      outgoing.foreach{ outgoing =>
        outgoing.link.findValueWithParam("rel", "wq") match {
          case Some(linkValue) =>
            val deliveryRef = DeliveryRef(outgoing.message_id,linkValue.uri)
            // basic deduplication.
            // If the message:uri has been routed once then it shouldn't be routed again.
            // We reuse the delivery-attempt-log as our deduplication evidence.
            Schema.tx {
              if (!Schema.deliveries.containsKey(deliveryRef)) {
                Schema.deliveries.put(deliveryRef, placeholderDelivery)
                Schema.outgoing.put(outgoing)
              } else {
                wqLog.info()
                wqLog.info(
                  "Outgoing message(%s) has already been sent to '%s'".format(
                    outgoing.message_id.message_id.map(_.toString).getOrElse("<message_id not available>"),
                    outgoing.link.toString()
                  )
                )
              }
            }
          case None =>
            wqLog.warn(
              "Outgoing message(%s) link is missing rel=\"wq\" link param: %s".format(
                outgoing.message_id.message_id.map(_.toString).getOrElse("<message_id not available>"),
                outgoing.link.toString()
              )
            )
        }
      }

  }

  /**
   * Recursivly process all of the bindings for the currentExchange, adding outgoing delivery to the given accumulator.
   * We keep a record of all prior exchanges we've processed to prevent duplicate delivery.
   */
  def processExchange(currentExchangeRef:ExchangeRef, previousExchangeRefs:ListBuffer[ExchangeRef], incoming:Incoming, _outgoingAccumulator:ListBuffer[Outgoing]):ListBuffer[Outgoing] = {
    // find the currentExchange's exchange type.
    // First look up currentExchange's arguments in the map of exchange_arguments map.
    // next find the 'type' argument.
    // then attempt to parse into a known ExchangeType value.
    // finally match the result.
    val currentExchangeArguments = collectionAsScalaIterableConverter(Schema.exchange_arguments.get(currentExchangeRef)).asScala
    currentExchangeArguments.find(_.key.map(Type.name == _).getOrElse(false)).map(_.value).flatMap(ExchangeType.parse(_)) match {
      case None =>
        wqLog.error(
          "could not find '%s' argument for exchange '%s'".format(
            "type",
            currentExchangeRef.exchange.getOrElse("<exchange name unavailable>")
          )
        )
        _outgoingAccumulator
      case Some(exchangeType) =>

        collectionAsScalaIterableConverter(Schema.bindings.get(currentExchangeRef)).asScala.foldLeft(_outgoingAccumulator) { (outgoingAccumulator,binding) =>
          (exchangeType, binding.binding) match {


            // Direct requires the routing key of the message to exactly match the routing key of the binding.
            case (Direct, Left(exchange))  =>
              if (exchange.routing_key == incoming.routing_key) {
                if (wqLog.isDebugEnabled) wqLog.debug(
                  "Message '%s' w/ routing-key '%s' matched key '%s' for direct exchange '%s'. Forwarding...".format(
                    incoming.message_id.message_id.getOrElse("<message id unavailable>"),
                    incoming.routing_key,
                    exchange.routing_key,
                    exchange.exchangeRef.exchange.getOrElse("<exchange name unavailable>")
                  )
                )
                processExchange(exchange.exchangeRef, previousExchangeRefs :+ currentExchangeRef, incoming, outgoingAccumulator)
              }
              else {
                if (wqLog.isDebugEnabled) wqLog.debug(
                  "Message '%s' w/ routing-key '%s' did not match key '%s' for direct exchange '%s'".format(
                    incoming.message_id.message_id.getOrElse("<message id unavailable>"),
                    incoming.routing_key,
                    exchange.routing_key,
                    exchange.exchangeRef.exchange.getOrElse("<queue name unavailable>")
                  )
                )
                outgoingAccumulator
              }
            case (Direct, Right(queue))    =>
              if (queue.routing_key == incoming.routing_key) {
                if (wqLog.isDebugEnabled) wqLog.debug(
                  "Message '%s' w/ routing-key '%s' matched key '%s' for queue '%s' bound to direct exchange '%s'. Creating Outgoing to %s".format(
                    incoming.message_id.message_id.getOrElse("<message id unavailable>"),
                    incoming.routing_key,
                    queue.routing_key,
                    queue.queueRef.queue.getOrElse("<queue name unavailable>"),
                    incoming.exchange.exchange.getOrElse("<exchange name unavailable>"),
                    queue.link.toString()
                  )
                )
                outgoingAccumulator :+ new Outgoing(incoming.message_id, queue.queueRef, incoming.headers, queue.link)
              }
              else {
                if (wqLog.isDebugEnabled) wqLog.debug(
                  "Message '%s' w/ routing-key '%s' did not match key '%s' for Queue '%s' bound to direct exchange '%s'".format(
                    incoming.message_id.message_id.getOrElse("<message id unavailable>"),
                    incoming.routing_key,
                    queue.routing_key,
                    queue.queueRef.queue.getOrElse("<queue name unavailable>"),
                    incoming.exchange.exchange.getOrElse("<exchange name unavailable>")
                  )
                )
                outgoingAccumulator
              }


            // Fanout ignores the message routing-key, sends the message to every binding the exchange hasß
            case (Fanout, Left(exchange))  =>
              if (wqLog.isDebugEnabled) wqLog.debug(
                "Message '%s'  matched for direct exchange '%s'. Forwarding...".format(
                  incoming.message_id.message_id.getOrElse("<message id unavailable>"),
                  exchange.exchangeRef.exchange.getOrElse("<exchange name unavailable>")
                )
              )
              processExchange(exchange.exchangeRef, previousExchangeRefs :+ currentExchangeRef, incoming, outgoingAccumulator)
            case (Fanout, Right(queue))    =>
              if (wqLog.isDebugEnabled) wqLog.debug(
                "Message '%s' matched queue '%s' bound to fanout exchange '%s'. Creating Outgoing to %s".format(
                  incoming.message_id.message_id.getOrElse("<message id unavailable>"),
                  queue.queueRef.queue.getOrElse("<queue name unavailable>"),
                  incoming.exchange.exchange.getOrElse("<exchange name unavailable>"),
                  queue.link.toString()
                )
              )
              outgoingAccumulator :+ new Outgoing(incoming.message_id, queue.queueRef, incoming.headers, queue.link)


            // Topic requires the message routing-key to match against a topic pattern, where periods seperate names, and names are single words or wildcard patterns.
            case (Topic, Left(exchange))  =>
              RoutingKey.parse(exchange.routing_key) match {
                case Left(err) =>
                  if (wqLog.isDebugEnabled) wqLog.debug(
                    "Message %s Failed attempt to parse routing-key '%s' for topic exchange '%s'".format(
                      incoming.message_id.message_id.getOrElse("<message id unavailable>"),
                      exchange.routing_key,
                      exchange.exchangeRef.exchange.getOrElse("<exchange name unavailable>")
                    )
                  )
                  outgoingAccumulator
                case Right(routing_key) if (!routing_key.pattern.matcher(incoming.routing_key).matches()) =>
                  if (wqLog.isDebugEnabled) wqLog.debug(
                    "Message '%s' w/ routing-key '%s' did not match key '%s' for topic exchange '%s'".format(
                      incoming.message_id.message_id.getOrElse("<message id unavailable>"),
                      incoming.routing_key,
                      exchange.routing_key,
                      exchange.exchangeRef.exchange.getOrElse("<queue name unavailable>")
                    )
                  )
                  outgoingAccumulator
                case Right(routing_key) =>
                  if (wqLog.isDebugEnabled) wqLog.debug(
                    "Message '%s' w/ routing-key '%s' matched key '%s' for topic exchange '%s'. Forwarding...".format(
                      incoming.message_id.message_id.getOrElse("<message id unavailable>"),
                      incoming.routing_key,
                      exchange.routing_key,
                      exchange.exchangeRef.exchange.getOrElse("<exchange name unavailable>")
                    )
                  )
                  processExchange(exchange.exchangeRef, previousExchangeRefs :+ currentExchangeRef, incoming, outgoingAccumulator)
              }
            case (Topic, Right(queue))    =>
              RoutingKey.parse(queue.routing_key) match {
                case Left(err) =>
                  if (wqLog.isDebugEnabled) wqLog.debug(
                    "Message %s Failed attempt to parse routing-key '%s' for topic exchange '%s'".format(
                      incoming.message_id.message_id.getOrElse("<message id unavailable>"),
                      queue.routing_key,
                      queue.queueRef.queue.getOrElse("<queue name unavailable>")
                    )
                  )
                  outgoingAccumulator
                case Right(routing_key) if (!routing_key.pattern.matcher(incoming.routing_key).matches()) =>
                  if (wqLog.isDebugEnabled) wqLog.debug(
                    "Message '%s' w/ routing-key '%s' did not match key '%s' for Queue '%s' bound to topic exchange '%s'".format(
                      incoming.message_id.message_id.getOrElse("<message id unavailable>"),
                      incoming.routing_key,
                      queue.routing_key,
                      queue.queueRef.queue.getOrElse("<queue name unavailable>"),
                      incoming.exchange.exchange.getOrElse("<exchange name unavailable>")
                    )
                  )
                  outgoingAccumulator
                case Right(routing_key) =>
                  if (wqLog.isDebugEnabled) wqLog.debug(
                    "Message '%s' w/ routing-key '%s' matched key '%s' for queue '%s' bound to topic exchange '%s'. Creating Outgoing to %s".format(
                      incoming.message_id.message_id.getOrElse("<message id unavailable>"),
                      incoming.routing_key,
                      queue.routing_key,
                      queue.queueRef.queue.getOrElse("<queue name unavailable>"),
                      incoming.exchange.exchange.getOrElse("<exchange name unavailable>"),
                      queue.link.toString()
                    )
                  )
                  outgoingAccumulator :+ new Outgoing(incoming.message_id, queue.queueRef, incoming.headers, queue.link)
              }

            // Headers not implemented
            case (Headers, Left(exchange))  =>
              outgoingAccumulator
              //processExchange(exchange.exchangeRef, previousExchangeRefs :+ currentExchangeRef, incoming, acc)
            case (Headers, Right(queue))    =>
              outgoingAccumulator
              //acc :+ new Outgoing(incoming.message_id, queue.link)

            // shouldnt happen
            case _ => throw new UnsupportedOperationException("Unsupported ExchangeType")
          }


        }
    }

  }
}
object Router {
  def createActor(actorNameFormat:String)(context:ActorContext): ActorRef = {
    context.actorOf(Props(new Router()),name = actorNameFormat.format(UUID.randomUUID.toString))
  }
}
