package de.tototec.sbuild

object SetProp {
  def apply(key: String, value: String)(implicit project: Project) =
    project.addProperty(key, value)
}

object Prop {
  def apply(key: String, value: String)(implicit project: Project) =
    project.properties.getOrElse(key, value)
  def apply(key: String)(implicit project: Project): String = project.properties(key)
}

