package de.tototec.sbuild

import java.io.File

object Module {
  def apply(dirOrFile: String)(implicit _project: Project): Unit = _project.findOrCreateModule(dirOrFile)
  def apply(dirOrFiles: String*)(implicit _project: Project): Unit =
    dirOrFiles.foreach { dirOrFile =>
      _project.findOrCreateModule(dirOrFile)
    }
}

