package org.sbuild.internal

import org.sbuild.Logger
import scala.reflect.ClassTag

object PrefixLogger {

  def apply[T: ClassTag](prefix: String): Logger = {
    val underlying = Logger[T]
    new Logger {
      override def error(msg: => String, throwable: Throwable) { underlying.error(prefix + msg, throwable) }
      override def warn(msg: => String, throwable: Throwable) { underlying.warn(prefix + msg, throwable) }
      override def info(msg: => String, throwable: Throwable) { underlying.info(prefix + msg, throwable) }
      override def debug(msg: => String, throwable: Throwable) { underlying.debug(prefix + msg, throwable) }
      override def trace(msg: => String, throwable: Throwable) { underlying.trace(prefix + msg, throwable) }
      override def toString = "Prefix(" + super.toString + ")"
    }
  }
}