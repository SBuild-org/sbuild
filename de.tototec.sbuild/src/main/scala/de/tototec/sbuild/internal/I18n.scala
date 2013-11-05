package de.tototec.sbuild.internal

import java.util.Locale
import scala.reflect.ClassTag
import scala.reflect.classTag
import java.util.MissingResourceException
import java.util.ResourceBundle
import java.text.MessageFormat

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
  def apply[T: ClassTag]: I18n = apply(Locale.getDefault)
  def apply[T: ClassTag](locale: Locale): I18n = new I18nImpl(classTag[T].runtimeClass, locale)
}

class I18nImpl(contextClass: Class[_], override val locale: Locale) extends I18n {

  private[this] def translate(msgid: String): String = try {
    val bundle = ResourceBundle.getBundle(contextClass.getPackage.getName + ".Messages", locale, contextClass.getClassLoader)
    bundle.getString(msgid)
  } catch {
    case e: MissingResourceException => msgid
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