package webhooq

import akka.actor.{Props, ActorRef, ActorContext, Actor}
import event.HazelcastQueueLister.QueueMessage
import webhooq.http._
import logging.WebhooqLogger
import webhooq.model.dao.{Delivery, DeliveryRef, Outgoing}
import java.util.UUID
import org.jboss.netty.channel.SimpleChannelHandler
import webhooq.event.HazelcastQueueLister.QueueMessage
import scala.Some
import webhooq.http.netty.HttpClient

//import webhooq.http.netty.{Success, Canceled, Err}

class Dispatcher() extends Actor with WebhooqLogger {
  def receive = {
    case  QueueMessage(outgoing:Outgoing) =>
      if (wqLog.isDebugEnabled) {
        wqLog.debug(
          "received Outgoing Message '%s' to '%s'".format(
            outgoing.message_id.message_id.getOrElse("<message_id unavailable"),
            outgoing.link.toString()
          )
        )
      }
      Option(Schema.messages.get(outgoing.message_id)) match {
        case None =>
          wqLog.error(
            "received Outgoing Message '%s' to '%s' however no message found by that id in the messages map!".format(
              outgoing.message_id.message_id.getOrElse("<message_id unavailable"),
              outgoing.link.toString()
            )
          )
        case Some(message) =>
          wqLog.info(
            "attempting to deliver Message '%s' to '%s'".format(
              message.toMD5(),
              outgoing.link.toString()
            )
          )
          outgoing.link.findValueWithParam("rel","wq") match {
            case None =>
              wqLog.error(
                "Message '%s' could not be delivered to link '%s', it did not contain a rel=\"wq\" link-param.".format(
                  message.toString(),
                  outgoing.link.toString()
                )
              )
            case Some(linkValue) =>
              wqLog.info(
                "attempting HTTP delivery call to '%s' for Message '%s' ".format(
                  linkValue.uri.toString(),
                  message.toMD5()
                )
              )
              try {
                HttpClient.call(
                  HttpRequest(
                    linkValue.linkParams.get("wq-method").flatMap(HttpMethod.parse(_)).getOrElse(HttpMethod.GET),
                    linkValue.uri,
                    outgoing.headers.toList,
                    message.body
                  ),
                  (response:HttpResponse) => {
                    // log the attempt in the delivery-attempt-log
                    val status = response.status.code
                    val deliveryRef = DeliveryRef(outgoing.message_id,linkValue.uri)
                    Schema.deliveries.put(deliveryRef, Delivery(System.currentTimeMillis(), status))


                    if (status < 200 && status > 299) {
                      wqLog.error(
                        "Message '%s' could not be delivered to link '%s', webhook returned: %s.".format(
                          message.toString(),
                          response
                        )
                      )
                      // TODO: Requeue message!!
                    } else {
                      wqLog.info(
                        "Message '%s' was successfully delivered to '%s'".format(
                          message.toMD5(),
                          linkValue.uri.toString()
                        )
                      )
                    }}
                )
              } catch {
                case e:Exception =>
                  wqLog.warn("Exception occurred while while attempting to deliver Message '%s' to '%s".format(
                    message.toMD5(),
                    linkValue.uri.toString()
                  ), e)
                  // TODO: Requeue message!!
              }
          }
      }

  }
}
object Dispatcher {
  def createActor(actorNameFormat:String)(context:ActorContext): ActorRef = {
    context.actorOf(Props(new Dispatcher()),name = actorNameFormat.format(UUID.randomUUID.toString))
  }
//  def createResponseActor(actorNameFormat:String,schema:Schema, context:ActorContext,outgoing:Outgoing): ActorRef = {
//    context.actorOf(Props(new DispatchResponse(schema,outgoing)),name = actorNameFormat.format(UUID.randomUUID.toString))
//  }
}

//class DispatchResponse(schema:Schema,outgoing:Outgoing) extends Actor with WebhooqLogger {
//  def receive = {
//    case Success =>
//      wqLog.info("Success:")
//    case Canceled =>
//      wqLog.info("Canceled")
//      schema.outgoing.put(outgoing)
//    case Err(cause) =>
//      wqLog.info("Error")
//      schema.outgoing.put(outgoing)
//  }
//}