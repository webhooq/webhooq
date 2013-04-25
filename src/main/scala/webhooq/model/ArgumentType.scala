package webhooq.model


abstract class ArgumentType(val name:String)
case object Type extends ArgumentType("type")
case object ArrivedAt extends ArgumentType("arrived-at")

object ArgumentType {
  def parse(input:String):Option[ArgumentType] = parse(Option(input))
  def parse(input:Option[String]):Option[ArgumentType] = input match {
    case Some(Type.name) => Some(Type)
    case Some(ArrivedAt.name) => Some(ArrivedAt)
    case _ => None
  }
}