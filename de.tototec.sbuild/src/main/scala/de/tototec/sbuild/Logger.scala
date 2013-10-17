package de.tototec.sbuild

import scala.reflect.ClassTag
import scala.reflect.classTag

trait Logger extends Serializable {
  def error(msg: => String, throwable: Throwable = null)
  def warn(msg: => String, throwable: Throwable = null)
  def info(msg: => String, throwable: Throwable = null)
  def debug(msg: => String, throwable: Throwable = null)
  def trace(msg: => String, throwable: Throwable = null)
}

object Logger {

  private[this] var cachedLoggerFactory: Option[String => Logger] = None

  private[this] lazy val noOpLogger = new Logger {
    override def error(msg: => String, throwable: Throwable) {}
    override def warn(msg: => String, throwable: Throwable) {}
    override def info(msg: => String, throwable: Throwable) {}
    override def debug(msg: => String, throwable: Throwable) {}
    override def trace(msg: => String, throwable: Throwable) {}
  }
  private[this] lazy val noOpLoggerFactory: String => Logger = _ => noOpLogger

  def apply[T: ClassTag]: Logger = cachedLoggerFactory match {

    case Some(loggerFactory) => loggerFactory(classTag[T].runtimeClass.getName)

    case None =>
      // try to load SLF4J
      def delegatedLoadingOfLoggerFactory: String => Logger = {
        // trigger loading of class, risking a NoClassDefFounError
        org.slf4j.LoggerFactory.getILoggerFactory()

        // if we are here, loading the LoggerFactory was successful
        loggedClass => new Logger {
          private[this] val underlying = org.slf4j.LoggerFactory.getLogger(loggedClass)
          override def error(msg: => String, throwable: Throwable) = if (underlying.isErrorEnabled) underlying.error(msg, throwable)
          override def warn(msg: => String, throwable: Throwable) = if (underlying.isWarnEnabled) underlying.warn(msg, throwable)
          override def info(msg: => String, throwable: Throwable) = if (underlying.isInfoEnabled) underlying.info(msg, throwable)
          override def debug(msg: => String, throwable: Throwable) = if (underlying.isDebugEnabled) underlying.debug(msg, throwable)
          override def trace(msg: => String, throwable: Throwable) = if (underlying.isTraceEnabled) underlying.trace(msg, throwable)
        }
      }

      try {
        val loggerFactory = delegatedLoadingOfLoggerFactory
        cachedLoggerFactory = Some(loggerFactory)
        loggerFactory(classTag[T].runtimeClass.getName)
      } catch {
        case e: NoClassDefFoundError => 
          cachedLoggerFactory = Some(noOpLoggerFactory)
          noOpLogger
      }
  }
}

