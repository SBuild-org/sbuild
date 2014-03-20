package de.tototec.sbuild.runner

object ParallelClassLoader {
  val isJava7: Boolean = try {
    classOf[ClassLoader].getDeclaredMethod("registerAsParallelCapable") != null
  } catch {
    case e: NoSuchMethodException => false
  }

  def withJava7[T](f: => T): Option[T] = if (isJava7) Some(f) else None
}
