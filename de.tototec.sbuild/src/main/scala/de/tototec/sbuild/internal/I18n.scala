package de.tototec.sbuild.internal

import java.util.Locale
import scala.reflect.ClassTag
import scala.reflect.classTag
import java.util.MissingResourceException
import java.util.ResourceBundle
import java.text.MessageFormat
import java.util.jar.JarInputStream
import java.io.File
import java.io.FileInputStream
import scala.collection.mutable.WeakHashMap
import de.tototec.sbuild.Logger

trait I18nMarker {
  @inline def marktr(msgid: String): String = msgid
  @inline def marktrc(context: String, msgid: String): String = msgid
}

trait I18n extends I18nMarker {
  def notr(msgid: String, params: Any*): String
  def tr(msgid: String, params: Any*): String
  def trn(msgid: String, msgidPlural: String, n: Long, params: Any*): String
  def trc(context: String, msgid: String, params: Any*): String
  def trcn(context: String, msgid: String, msgidPlural: String, n: Long, params: Any*): String
  def locale: Locale
}

object I18n extends I18nMarker {

  private[this] val log = Logger[I18n.type]

  val CatalogBaseName = "I18n-Catalog"

  var missingTranslationDecorator: Option[String => String] = None

  private[this] val packageToCatalogBaseNameMap = WeakHashMap[Package, Option[String]]()

  def apply[T: ClassTag]: I18n = apply(Locale.getDefault)
  def apply[T: ClassTag](locale: Locale): I18n = {

    val runtimeClass = classTag[T].runtimeClass

    val manifestCatalogBaseName = packageToCatalogBaseNameMap.get(runtimeClass.getPackage()) match {
      case Some(baseNameOption) =>
        baseNameOption
      case None => try {
        log.debug("About to lookup the manifest for class: " + runtimeClass)
        val jarLoc = runtimeClass.getProtectionDomain().getCodeSource().getLocation()
        val jarFile = new File(jarLoc.toURI())
        log.debug("JAR location: " + jarFile)
        val stream = new JarInputStream(new FileInputStream(jarFile))
        val baseNameOption = try {
          Option(stream.getManifest().getMainAttributes().getValue(CatalogBaseName))
        } finally {
          stream.close
          None
        }
        log.debug("Determined manifest entry for message catalog basename: " + baseNameOption)
        synchronized { packageToCatalogBaseNameMap += runtimeClass.getPackage() -> baseNameOption }
        baseNameOption
      }
    }

    val catalogBaseName = manifestCatalogBaseName match {
      case Some(baseName) =>
        log.debug("Using message catalog basename from Manifest: " + baseName)
        baseName
      case None =>
        val baseName = runtimeClass.getPackage.getName + ".Messages"
        log.debug("Using message catalog basename derived from package name: " + baseName)
        baseName
    }

    new I18nImpl(catalogBaseName, runtimeClass.getClassLoader(), locale, None)
  }
}

class I18nImpl(catalogBaseName: String, classLoader: ClassLoader, override val locale: Locale, missingTranslationDecorator: Option[String => String]) extends I18n {

  private[this] lazy val log = Logger[I18nImpl]

  override def toString = getClass.getSimpleName + "(catalogBaseName=" + catalogBaseName + ",locale=" + locale + ")"

  private[this] def translate(msgid: String): String = try {
    ResourceBundle.getBundle(catalogBaseName, locale, classLoader).getString(msgid)
  } catch {
    case e: MissingResourceException =>
      log.trace("Could not find msgid \"" + msgid + "\" in: " + this, e)
      missingTranslationDecorator match {
        case None => I18n.missingTranslationDecorator match {
          case None => msgid
          case Some(miss) => miss(msgid)
        }
        case Some(miss) => miss(msgid)
      }
  }

  override def notr(msgid: String, params: Any*): String = params match {
    case Seq() => msgid
    case _ => MessageFormat.format(msgid, params.map(_.asInstanceOf[AnyRef]): _*)
  }

  override def tr(msgid: String, params: Any*): String = notr(translate(msgid), params: _*)

  override def trn(msgid: String, msgidPlural: String, n: Long, params: Any*): String = tr(n match {
    case 1 => msgid
    case _ => msgidPlural
  }, params: _*)

  override def trc(context: String, msgid: String, params: Any*): String = tr(msgid, params: _*)

  override def trcn(context: String, msgid: String, msgidPlural: String, n: Long, params: Any*): String = trn(msgid, msgidPlural, n, params: _*)

}