package de.tototec.sbuild

object Module {
  def apply(dirOrFile: String)(implicit _project: Project) = _project.findOrCreateModule(dirOrFile)
  def apply(dirOrFiles: String*)(implicit _project: Project) =
    dirOrFiles.foreach { dirOrFile =>
      _project.findOrCreateModule(dirOrFile)
    }
}

