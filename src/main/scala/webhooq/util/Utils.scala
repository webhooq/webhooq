package webhooq.util

import java.security.MessageDigest


object Utils {
  /**
   * thanks: http://www.rgagnon.com/javadetails/java-0596.html
   */
  def array2hex(a:Array[Byte]):String = {
    val HEXES = "0123456789abcdef"
    a.foldLeft(new StringBuilder( 2 * a.length )) { (s,b) =>
      s.append(HEXES.charAt((b & 0xF0) >> 4)).append(HEXES.charAt(b & 0x0F))
    }.toString()
  }

  def md5(a:Array[Byte]):String = {
    val digest = MessageDigest.getInstance("MD5")
    digest.update(a)
    array2hex(digest.digest())
  }
}
