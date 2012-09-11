package de.tototec.sbuild.eclipse.plugin

import org.eclipse.core.runtime.IPath
import org.eclipse.jdt.core.JavaCore
import org.eclipse.jdt.core.IClasspathEntry
import org.eclipse.core.runtime.Path

object Settings {
  val SBuildFileKey = "sbuildFile"
  val SBuildFileDefault = "SBuild.scala"

  val ExportedClasspathKey = "exportedClasspath"
  val ExportedClasspathDefault = "eclipse.classpath"

  val RelaxedFetchOfDependenciesKey = "relaxedFetchOfDependencies"
  val RelaxedFetchOfDependenciesDefault = true.toString

  val WorkspaceProjectAliasKey = "workspaceProjectAlias:"
}

import de.tototec.sbuild.eclipse.plugin.Settings._

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
    debug("Loaded from path: " + containerPath + " the options: " + options)
  }

  def fromIClasspathEntry(classpathEntry: IClasspathEntry) {
    classpathEntry match {
      case null => options = Map()
      case cpe => fromPath(classpathEntry.getPath)
    }
  }

  def sbuildFile: String = options.getOrElse(SBuildFileKey, SBuildFileDefault)
  def sbuildFile_=(sbuildFile: String) = sbuildFile match {
    case null => options -= SBuildFileKey
    case x if x.trim == "" => options -= SBuildFileKey
    case x if x == SBuildFileDefault => options -= SBuildFileKey
    case x => options += (SBuildFileKey -> x)
  }

  def exportedClasspath: String = options.getOrElse(ExportedClasspathKey, ExportedClasspathDefault)
  def exportedClasspath_=(exportedClasspath: String) = exportedClasspath match {
    case null => options -= ExportedClasspathKey
    case x if x.trim == "" => options -= ExportedClasspathKey
    case x if x == ExportedClasspathDefault => options -= ExportedClasspathKey
    case x => options += (ExportedClasspathKey -> x)
  }

  def relaxedFetchOfDependencies =
    options.getOrElse(RelaxedFetchOfDependenciesKey, RelaxedFetchOfDependenciesDefault) == true.toString
  def relaxedFetchOfDependencies_=(relaxedFetchOfDependencies: Boolean) = relaxedFetchOfDependencies.toString match {
    case RelaxedFetchOfDependenciesDefault => options -= RelaxedFetchOfDependenciesKey
    case x => options += (RelaxedFetchOfDependenciesKey -> x)
  }
}