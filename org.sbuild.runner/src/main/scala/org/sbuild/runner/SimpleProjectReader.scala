package org.sbuild.runner

import java.io.File
import java.lang.reflect.InvocationTargetException
import org.sbuild.CmdlineMonitor
import org.sbuild.Project
import org.sbuild.ProjectPool
import org.sbuild.ProjectReader
import org.sbuild.internal.BuildFileProject
import org.sbuild.NoopCmdlineMonitor
import org.sbuild.SchemeHandler
import org.sbuild.Plugin
import org.sbuild.ScanSchemeHandler
import org.sbuild.MapperSchemeHandler
import org.sbuild.internal.Bootstrapper
import org.sbuild.internal.PluginAwareImpl
import org.sbuild.SBuildVersion
import org.sbuild.RichFile

class SimpleProjectReader(
  classpathConfig: ClasspathConfig,
  monitor: CmdlineMonitor = NoopCmdlineMonitor,
  clean: Boolean = false,
  fileLocker: FileLocker,
  initialProperties: Map[String, String] = Map())
    extends ProjectReader {

  private[this] val projectScript = new ProjectScript(classpathConfig.classpaths, fileLocker, noFsc = classpathConfig.noFsc)

  override def readAndCreateProject(projectFile: File, properties: Map[String, String], projectPool: Option[ProjectPool], monitor: Option[CmdlineMonitor]): Project = {
    val projectMonitor = monitor.getOrElse(this.monitor)

    if (clean) {
      import org.sbuild.toRichFile
      val dir = (projectFile match {
        case d if d.isDirectory() => d
        case f => f.getParentFile()
      }) / ".sbuild"
      if (dir.exists()) {
        monitor.map(_.info(CmdlineMonitor.Verbose, "Deleting dir: " + dir))
        dir.deleteRecursive
      }
    }

    try {
      //  Compile Script and load compiled class
      val script = projectScript.loadScriptClass(projectFile, projectMonitor)

      val project = new BuildFileProject(projectFile, this, projectPool, Some(script.scriptEnv.typesToIncludedFilesPropertiesFile), monitor = projectMonitor)

      initialProperties.foreach {
        case (key, value) => project.addProperty(key, value)
      }
      properties.foreach {
        case (key, value) => project.addProperty(key, value)
      }

      script.applyToProject(project)

      project.finalizePlugins

      projectPool.map { pool => pool.addProject(project) }
      project

    } catch {
      case e: InvocationTargetException =>
        Console.err.println("Errors in build script: " + projectFile)
        e.getCause match {
          case null => throw e
          case c => throw c
        }
    }
  }

  override def close(): Unit = {
    projectScript.dropCaches()
  }

}

