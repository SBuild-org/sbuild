package de.tototec.sbuild.eclipse.plugin

import org.eclipse.core.runtime.IPath
import org.eclipse.jdt.core.JavaCore
import org.eclipse.jdt.core.IClasspathEntry
import org.eclipse.core.runtime.Path

class Settings {
  def this(containerPath: IPath) = {
    this
    fromPath(containerPath)
  }

  private var options: Map[String, String] = Map()

  def toIClasspathEntry: IClasspathEntry = {
    JavaCore.newContainerEntry(new Path(
      SBuildClasspathContainer.ContainerName + "/" +
        options.map {
          case (k, v) => k + "=" + v
        }.mkString(",")
    ))
  }

  def fromPath(containerPath: IPath) {
    options = containerPath.segmentCount() match {
      case 0 | 1 => Map()
      case _ =>
        containerPath.lastSegment.split(",").map {
          _.split("=", 2) match {
            case Array(key, value) => (key, value)
            case Array(key) => (key, true.toString)
          }
        }.toMap
    }
  }

  def fromIClasspathEntry(classpathEntry: IClasspathEntry) {
    classpathEntry match {
      case null => options = Map()
      case cpe => fromPath(classpathEntry.getPath)
    }
  }

  def exportedClasspath: String = options.getOrElse("exportedClasspath", "eclipse.classpath")
  def exportedClasspath_=(exportedClasspath: String) = exportedClasspath match {
    case null => options -= "exportedClasspath"
    case x if x.trim == "" => options -= "exportedClasspath"
    case x if x == "eclipse.classpath" => options -= "exportedClasspath"
    case x => options += ("exportedClasspath" -> exportedClasspath)
  }

  //  def workspaceProjectAliases: Map[String, String] 

  //  def workspaceResolution: Boolean = options.getOrElse("workspaceResolution", "true").toBoolean
  //  def workspaceResolution_=(resolveFromWorkspace: Boolean) = options += ("workspaceResolution" -> resolveFromWorkspace.toString)

}