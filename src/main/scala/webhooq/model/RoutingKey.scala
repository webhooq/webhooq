package webhooq.model

import java.util.regex.Pattern
import util.parsing.combinator.RegexParsers
import webhooq.util.LRUCache


case class RoutingKey(val key:String, val regex:String, val pattern:Pattern)

/**
 * Entry port for compiling a routing key string into a RoutingKey instance.
 * The "compilation" result is a <code>Pattern</code> instance.
 * Since the parsing of the routing key string into a regex string, then again into a Pattern is expensive, the result
 * will be cached in a local LRU cache.
 */
object  RoutingKey {
  val cache = new LRUCache[String, RoutingKey](
    Option(System.getProperty("wq.cache.routing_key")).map(s => try {s.toInt} catch {case e:NumberFormatException => 10000}).getOrElse(10000)
  )

  def parse(key:String): Either[String,RoutingKey] = {
    cache.get(key) match {
      case Some(rk) => Right(rk)
      case None =>
        parseKeyToRegex(key) match {
          case Left(err)    => Left(err)
          case Right(regex) => Option(Pattern.compile(regex)) match {
            case None => Left("%s failed to compile".format(regex))
            case Some(pattern) =>
              val rk = RoutingKey(key,regex, pattern)
              cache + (key -> rk)
              Right(rk)
          }
        }
    }

  }

  def parseKeyToRegex(key:String): Either[String,String] = {
    RouteParser.parse(key) match {
      case Left(err) => Left(err)
      case Right(rs) =>
        val res = rs.foldLeft( (List.empty[String], Option.empty[Route]) ) { (acc,r) =>
          val list = acc._1
          val prev = acc._2
          if (prev == Some(All)) {acc}
          else {
            r match {
              case p@All => (list :+ "(.*)", Some(p))
              case p@Any => (list :+ "([^.#*]+)", Some(p))
              case p@Named(name) => (list :+ name, Some(p))
            }
          }
        }._1
        Right("^" + res.mkString("\\.") + "$")
    }
  }

  abstract class Route
  case class Named(name:String) extends Route
  case object Any extends Route
  case object All extends Route

  object RouteParser extends RegexParsers {
    def route:Parser[Route] = all | any | named
    def all:Parser[Route] = "#" ^^ {case _ => All }
    def any:Parser[Route] = "*" ^^ {case _ => Any }
    def named:Parser[Route] = """([^\.]+)""".r ^^ { case s => Named(s)}
    def parse (inputString:String):Either[String,List[Route]] = {
      this.parse(repsep(route,"."), inputString) match {
        case Success(routeValues, _   ) => Right(routeValues)
        case NoSuccess(message,  next) => Left(message)
        case Failure(message  ,  next) => Left(message)
      }
    }
  }

  def main(args:Array[String]) = {
    val testKey = "a.*.c.#"
    RoutingKey.parseKeyToRegex(testKey) match {
      case Left(perr) => println("parse error:"+perr)
      case Right(rs) =>
        println("success:"+rs)
        RoutingKey.parse(testKey) match {
          case Left(cerr) =>  println("parse error:"+cerr)
          case Right(rkey) =>
            println("compile success!")
            val should1 = rkey.pattern.matcher("a.b.c.d").matches()
            val should2 = rkey.pattern.matcher("a.e.c").matches()
            val should3 = rkey.pattern.matcher("a.b.c.e.d.f.g") .matches()
            val shouldnt1 = rkey.pattern.matcher("b.c.d") .matches()
            val shouldnt2 = rkey.pattern.matcher("a.c.d") .matches()
            println("should1:   "+should1)
            println("should2:   "+should2)
            println("should3:   "+should3)
            println("shouldnt1: "+shouldnt1)
            println("shouldnt2: "+shouldnt2)
            println("cache dump:")
            RoutingKey.cache.foreach(e => println("\t"+e._1))
        }
    }
  }
}