package webhooq.model

object ExchangeType {
  def parse(input:Option[String]): Option[ExchangeType] = input match {
    case Some(Direct.name)  => Some(Direct)
    case Some(Fanout.name)  => Some(Fanout)
    case Some(Topic.name)   => Some(Topic)
    case Some(Headers.name) => Some(Headers)
    case _ => None
  }
  def parse(input:String): Option[ExchangeType] = parse(Some(input))
}
abstract class ExchangeType(val name:String)
case object Direct extends ExchangeType("direct")
case object Fanout extends ExchangeType("fanout")
case object Topic extends ExchangeType("topic")
case object Headers extends ExchangeType("headers")
