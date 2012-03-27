package de.tototec.sbuild

object Prop {
  def apply(key: String, value: String)(implicit project: Project) =
    project.addProperty(key, value)
  def apply(key: String)(implicit project: Project): String = project.properties(key)
}

