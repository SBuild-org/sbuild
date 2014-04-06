package org.sbuild.internal

import java.security.MessageDigest

object Md5 {

  val md = MessageDigest.getInstance("MD5")

  def md5sum(content: String): String = md.digest(content.getBytes()).
    foldLeft("")((string, byte) => string + Integer.toString((byte & 0xff) + 0x100, 16).substring(1))

}