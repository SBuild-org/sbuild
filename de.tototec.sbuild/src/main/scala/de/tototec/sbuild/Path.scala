package de.tototec.sbuild

import java.io.File
import scala.reflect.ClassTag

object Path {

  // since SBuild 0.4.0.9002
  def apply[T: ClassTag](path: String, paths: String*)(implicit project: Project): File = {
    val baseDir = project.includeDirOf[T]
    val file = normalize(new File(path), baseDir)
    if (paths.isEmpty) {
      file
    } else {
      paths.foldLeft(file)((f, e) => new File(f, e))
    }
  }

  def apply(path: String, paths: String*)(implicit project: Project): File = {
    val file = normalize(new File(path), project.projectDirectory)
    if (paths.isEmpty) {
      file
    } else {
      paths.foldLeft(file)((f, e) => new File(f, e))
    }
  }

  def normalize(path: File, baseDir: File = new File(".")): File = {
    val absFile = if (path.isAbsolute) path else new File(baseDir, path.getPath)
    new File(absFile.toURI.normalize)
  }

}

// since SBuild 0.3.1.9000
@deprecated("Use Paths instead.", "0.4.0.9002")
object Pathes {
  def apply(paths: Seq[String])(implicit project: Project): Seq[File] =
    paths.map(path => Path(path))
}

// since SBuild 0.4.0.9002
object Paths {
  def apply(paths: Seq[String])(implicit project: Project): Seq[File] =
    paths.map(path => Path(path))
}
