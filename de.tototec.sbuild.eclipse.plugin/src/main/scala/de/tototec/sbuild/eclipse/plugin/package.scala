package de.tototec.sbuild.eclipse

package object plugin {
  /** Print a debug message. */
  private[plugin] def debug(msg: => String) = {
    Console.println(msg)
  }
}