package webhooq.http

/**
 */
object WebhooqHeader {
  val EXCHANGE     = HttpHeader("x-wq-exchange")    // exchange_name
  val QUEUE        = HttpHeader("x-wq-queue")       // queue_name
  val ROUTING_KEY  = HttpHeader("x-wq-routing-key") // an AMQP routing key
  val LINK         = HttpHeader("x-wq-link")        // an RFC-5988 Link Header value
  val MESSAGE_ID   = HttpHeader("x-wq-msg-id")      // UUID
}
