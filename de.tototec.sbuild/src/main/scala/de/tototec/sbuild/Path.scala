package de.tototec.sbuild

import java.io.File

object Path {
  def apply(path: String, pathes: String*)(implicit project: Project): File = {
    val file = {
      val origFile = new File(path)
      if (origFile.isAbsolute) {
        origFile.getCanonicalFile
      } else {
        val absFile = new File(project.projectDirectory, path)
        absFile.getCanonicalFile
      }
    }
    if(pathes.isEmpty) {
      file
    }
    else {
      pathes.foldLeft(file)((f, e) => new File(f, e))
    }
  }
}

