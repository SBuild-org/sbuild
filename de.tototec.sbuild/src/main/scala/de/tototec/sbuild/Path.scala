package de.tototec.sbuild

import java.io.File
import scala.reflect.ClassTag

object Path {
  
  // since SBuild 0.4.0.9002
  def apply[T : ClassTag](path: String, pathes: String*)(implicit project: Project): File = {
    val baseDir = project.includeDirOf[T]
    val file = normalize(new File(path), baseDir)
    if (pathes.isEmpty) {
      file
    } else {
      pathes.foldLeft(file)((f, e) => new File(f, e))
    }
  }

  def apply(path: String, pathes: String*)(implicit project: Project): File = {
    val file = normalize(new File(path), project.projectDirectory)
    if (pathes.isEmpty) {
      file
    } else {
      pathes.foldLeft(file)((f, e) => new File(f, e))
    }
  }

  def normalize(path: File, baseDir: File = new File(".")): File = {
    val absFile = if (path.isAbsolute) path else new File(baseDir, path.getPath)
    new File(absFile.toURI.normalize)
  }

}

// since SBuild 0.3.1.9000
object Pathes {
  def apply(pathes: Seq[String])(implicit project: Project): Seq[File] =
    pathes.map(path => Path(path))
}
