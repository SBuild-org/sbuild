package org.sbuild.internal

import java.util.ArrayList

object StringSplitter {

  def split(stringToSplit: String,
            delim: String,
            delimMask: Option[String] = None,
            maxCount: Int = 0): Seq[String] = {

    val delimMaskLength = delimMask match {
      case Some(m) => m.length
      case None => 0
    }

    /** Find next part to be split and return the rest. */
    def find(parts: Seq[String], current: String, rest: String): Seq[String] = {
      if (rest == "") {
        parts ++ Seq(current)
      } else if (rest.startsWith(delim)) {
        find(parts ++ Seq(current), "", rest.substring(delimMaskLength))
      } else if (delimMask.isDefined && rest.startsWith(delimMask.get)) {
        if (rest.size > delimMaskLength) {
          find(parts, current + rest.substring(delimMaskLength, delimMaskLength + 1), rest.substring(delimMaskLength + 1))
        } else {
          parts ++ Seq(current + rest)
        }
      } else {
        find(parts, current + rest.substring(0, 1), rest.substring(1))
      }
    }

    find(Seq(), "", stringToSplit)
  }

}
