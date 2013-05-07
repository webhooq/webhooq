package webhooq.model

import java.net.URI
import util.parsing.combinator.RegexParsers


/**
 * An RFC-5988 Link is a collection of LinkValues. A link header may contain more than one URI,
 * each with it's own map of parameters. We model each URI+Parameters as a LinkValue instance.
 */
case class Link(linkValues:List[LinkValue]) {

  /**
   * Find a LinkValue that has a Link Parameter with the given paramName and the given paramValue.
   */
  def findValueWithParam (paramName:String, paramValue:String): Option[LinkValue] =
    linkValues.find(_.linkParams.find(t=>t._1 == paramName && t._2 == paramValue).isDefined)

  override def toString(): String = {
    linkValues.foldLeft( (new StringBuilder(), true) ) { (sbt,lv) =>
      val first = sbt._2
      val strbd  = sbt._1
      if (!first) strbd.append(",")
      (lv.format(strbd), false)
    }._1.toString()
  }
}
object Link {
  def parse (input:String): Option[Link] = {
    LinkValueParser.parse(input) match {
      case Left(_) => None
      case Right(linkValues) => Some(Link(linkValues))
    }
  }
}

/**
 * Models a tuple of a URI and a Map of parameters.
 */
case class LinkValue(uri:URI, linkParams:Map[String,String]) {
  def format(sb:StringBuilder):StringBuilder  = {
    sb.append("<").append(uri.toString).append(">;")
    linkParams.foreach {kv => sb.append(kv._1).append("=\"").append(kv._2).append("\";") }
    sb
  }
}

/**
 * A Parser Combinator that implements a fairly liberal interpretation of [RFC-5988](http://tools.ietf.org/html/rfc5988)'s Link ABNF grammar.
 * We don't distinguish link-params from link-extension, rather just treating them all as link-extensions.
 */
object LinkValueParser  extends RegexParsers {
  def `link-value`    : Parser[LinkValue]       = ("<" ~> `URI-Reference` <~ ">") ~ rep(`link-params`)   ^^ {case uri  ~ ps => LinkValue(uri, ps.toMap) }
  def `link-params`   : Parser[(String,String)] = ";" ~ `link-param`                                     ^^ {case ";"  ~ linkParams => linkParams}
  def `link-param`    : Parser[(String,String)] = `param-name` ~ ("=\"" ~> `param-value` <~ "\"")        ^^ {case name ~ value => (name,value)}
  def `URI-Reference` : Parser[URI]             = """([^>]*)""".r                                        ^^ {case uri  => new URI(uri) }
  def `param-name`    : Parser[String]          = """([^=]*)""".r
  def `param-value`   : Parser[String]          = """([^"]*)""".r

  def parse(inputString:String):Either[String,List[LinkValue]] = {
    this.parse(repsep(`link-value`,","), inputString) match {
      case Success(linkValues, _   ) => Right(linkValues)
      case NoSuccess(message,  next) => Left(message)
      case Failure(message,     next) => Left(message)
    }
  }
  def parseValue(inputString:String):Either[String, LinkValue] = {
    this.parse(`link-value`, inputString) match {
      case Success(linkValue, _    ) => Right(linkValue)
      case NoSuccess(message,  next) => Left(message)
      case Failure(message,    next) => Left(message)
    }
  }
}
