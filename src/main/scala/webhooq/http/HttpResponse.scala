package webhooq.http


case class HttpResponse (status:HttpStatus, headers:List[(String,String)] = List.empty[(String,String)], body:Array[Byte] = Array.empty[Byte]) {
  def getHeader(headerName:String):Option[String] ={
    headers.find{case (header,_) => headerName == header}.map(_._2)
  }
}