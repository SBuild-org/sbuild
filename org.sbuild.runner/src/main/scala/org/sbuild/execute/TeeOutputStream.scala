package org.sbuild.execute

import java.io.PrintStream

object ContextAwareTeeOutputStream {

  case class TeeConfig(copyStream: PrintStream)

  private val tl = new InheritableThreadLocal[Map[ContextAwareTeeOutputStream, TeeConfig]]() {
    override protected def initialValue(): Map[ContextAwareTeeOutputStream, TeeConfig] = Map()
  }

  def setConfig(tee: ContextAwareTeeOutputStream, config: Option[TeeConfig]): Option[TeeConfig] = {
    val orig = tl.get().get(tee)
    config match {
      case Some(config) => tl.set(tl.get() ++ Map(tee -> config))
      case None => tl.set(tl.get().filterKeys(tee !=))
    }
    orig
  }

  def liftAndSetStdOut(config: Option[TeeConfig]): Option[TeeConfig] = System.out match {
    case tee: ContextAwareTeeOutputStream => setConfig(tee, config)
    case _ => None
  }

  def liftAndSetStdErr(config: Option[TeeConfig]): Option[TeeConfig] = System.err match {
    case tee: ContextAwareTeeOutputStream => setConfig(tee, config)
    case _ => None
  }

}

class ContextAwareTeeOutputStream(allOutput: PrintStream, autoFlush: Boolean = false)
    extends PrintStream(allOutput, autoFlush) {
  import ContextAwareTeeOutputStream._

  private[this] def withConfig(f: TeeConfig => Unit): Unit = tl.get() match {
    case null =>
    case map => map.get(this) foreach (c => f(c))

  }

  private[this] def withCopyStream(f: PrintStream => Unit): Unit = withConfig { config =>
    f(config.copyStream)
  }

  override def write(int: Int): Unit = synchronized {
    super.write(int)
    withCopyStream { copy => copy.write(int) }
  }

  override def write(buffer: Array[Byte], offset: Int, length: Int): Unit = synchronized {
    super.write(buffer, offset, length)
    withCopyStream { copy => copy.write(buffer, offset, length) }
  }

  override def flush(): Unit = synchronized {
    super.flush()
    withCopyStream { copy => copy.flush() }
  }

  override def close(): Unit = synchronized {
    super.close()
    withCopyStream { copy => copy.close() }
  }

}
