package de.tototec.sbuild

import java.io.File

object Path {
  def apply(path: String)(implicit project: Project): File = project.uniqueFile(path)
}

