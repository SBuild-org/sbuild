package org.sbuild

import org.sbuild.internal.I18n

object SetProp {
  def apply(key: String, value: String)(implicit project: Project) =
    project.addProperty(key, value)
}

object Prop {
  def apply(key: String, value: String)(implicit project: Project): String =
    project.properties.getOrElse(key, value)
  def apply(key: String)(implicit project: Project): String = if (project.properties.contains(key)) {
    project.properties(key)
  } else {
    val i18n = I18n[Prop.type]
    val msg = i18n.marktr("Undefined property \"{0}\" accessed. Please define it e.g. with \"-D {0}=value\".")
    throw new ProjectConfigurationException(i18n.notr(msg, key), null, i18n.tr(msg, key))
  }
  def get(key: String)(implicit project: Project): Option[String] = project.properties.get(key)
}

