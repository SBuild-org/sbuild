package de.tototec.sbuild

object SetProp {
  def apply(key: String, value: String)(implicit project: Project) =
    project.addProperty(key, value)
}

object Prop {
  def apply(key: String, value: String)(implicit project: Project) =
    project.properties.getOrElse(key, value)
  def apply(key: String)(implicit project: Project): String = if (project.properties.contains(key)) {
    project.properties(key)
  } else {
    throw new ProjectConfigurationException("Undefined property \"" + key + "\" accessed. Please define it e.g. with \"-D " + key + "=value\".")
  }
  def get(key: String)(implicit project: Project): Option[String] = project.properties.get(key)
}

