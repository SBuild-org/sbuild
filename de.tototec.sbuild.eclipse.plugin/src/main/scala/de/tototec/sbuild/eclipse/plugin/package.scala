package de.tototec.sbuild.eclipse

package object plugin {

  /** Print a debug message. */
  private[plugin] def debug(msg: => String, cause: Throwable = null) = {
    Console.err.println(msg)
    if(cause != null) {
      Console.err.println(cause.getMessage())
      cause.printStackTrace(Console.err)
    }
  }

}
