package webhooq.http


case class HttpStatus(val code:Int, val reasonPhrase:String)
object HttpStatus {
  def OK          (reason:String = "OK")          = new HttpStatus(200,reason)
  def CREATED     (reason:String = "Created")     = new HttpStatus(201,reason)
  def ACCEPTED    (reason:String = "Accepted")    = new HttpStatus(202,reason)
  def NO_CONTENT  (reason:String = "No Content")  = new HttpStatus(204,reason)

  def BAD_REQUEST (reason:String = "Bad Request") = new HttpStatus(400,reason)
  def NOT_FOUND   (reason:String = "Not Found")   = new HttpStatus(404,reason)
}
