package webhooq

import akka.actor.{Props, ActorRef, ActorContext, Actor}
import event.HazelcastQueueLister.QueueMessage
import webhooq.http._
import logging.WebhooqLogger
import model.dao.Outgoing
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
              message.toVerbose(),
              outgoing.link.toString()
            )
          )
          outgoing.link.findValueWithParam("rel","wq") match {
            case None =>
              wqLog.error(
                "Message '%s' could not be delivered to link '%s', it did not contain a rel=\"wq\" link-param.".format(
                  message.toVerbose(),
                  outgoing.link.toString()
                )
              )
            case Some(linkValue) =>
              wqLog.info(
                "attempting to deliver Message '%s' to '%s'".format(
                  message.toVerbose(),
                  linkValue.uri.toString()
                )
              )
              HttpClient.call(
                HttpRequest(
                  linkValue.linkParams.get("wq-method").flatMap(HttpMethod.parse(_)).getOrElse(HttpMethod.GET),
                  linkValue.uri,
                  outgoing.headers.toList,
                  message.body
                ),
                (response:HttpResponse) =>
                  if (response.status.code < 200 && response.status.code > 299) {
                    wqLog.error(
                      "Message '%s' could not be delivered to link '%s', webhook returned: %s.".format(
                        message.toVerbose(),
                        response
                      )
                    )
                  } else {
                    wqLog.info(
                      "Message '%s' was successfully delivered to '%s'".format(
                        message.toVerbose(),
                        linkValue.uri.toString()
                      )
                    )
                    //TODO: remove message from messages map, update dedupe cache.
                  }
              )
//              HttpClient.connect(
//                linkValue.uri,
//                Dispatcher.createResponseActor("response-%s",schema, this.context, outgoing),
//                Map[String,SimpleChannelHandler]("handler" -> new DeliveryResponseHandler())
//              )
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