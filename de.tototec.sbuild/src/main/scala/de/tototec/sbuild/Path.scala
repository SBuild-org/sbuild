package de.tototec.sbuild

import java.io.File

object Path {
  def apply(path: String)(implicit project: Project): File = {
    val origFile = new File(path)
    if (origFile.isAbsolute) {
      origFile.getCanonicalFile
    } else {
      val absFile = new File(project.projectDirectory, path)
      absFile.getCanonicalFile
    }
  }
}

