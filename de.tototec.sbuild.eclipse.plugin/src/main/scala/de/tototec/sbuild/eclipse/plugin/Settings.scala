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
    val read: Map[String, String] = if (containerPath.segmentCount() > 1) {
      containerPath.lastSegment.split(",").map {
        _.split("=", 2) match {
          case Array(key, value) => (key, value)
          case Array(key) => (key, true.toString)
        }
      }.toMap
    } else {
      Map()
    }

    options = read
  }

  def fromIClasspathEntry(classpathEntry: IClasspathEntry) {
    classpathEntry match {
      case null =>
        options = Map()
      case cpe =>
        fromPath(classpathEntry.getPath)
    }
  }

  def workspaceResolution: Boolean = options.getOrElse("workspaceResolution", "true").toBoolean
  def workspaceResolution_=(resolveFromWorkspace: Boolean) = options += ("workspaceResolution" -> resolveFromWorkspace.toString)

}