package de.tototec.sbuild.runner

import java.io.File
import java.lang.reflect.InvocationTargetException
import de.tototec.sbuild.CmdlineMonitor
import de.tototec.sbuild.Project
import de.tototec.sbuild.ProjectPool
import de.tototec.sbuild.ProjectReader
import de.tototec.sbuild.internal.BuildFileProject
import de.tototec.sbuild.NoopCmdlineMonitor

class SimpleProjectReader(
  classpathConfig: ClasspathConfig,
  monitor: CmdlineMonitor = NoopCmdlineMonitor,
  clean: Boolean = false,
  fileLocker: FileLocker,
  initialProperties: Map[String, String] = Map())
    extends ProjectReader {

  override def readAndCreateProject(projectFile: File, properties: Map[String, String], projectPool: Option[ProjectPool], monitor: Option[CmdlineMonitor]): Project = {
    val script = new ProjectScript(projectFile, classpathConfig, monitor.getOrElse(this.monitor), fileLocker)
    if (clean) {
      script.clean
    }

    val project = new BuildFileProject(projectFile, this, projectPool, Some(script.typesToIncludedFilesPropertiesFile), monitor = monitor.getOrElse(this.monitor))

    initialProperties.foreach {
      case (key, value) => project.addProperty(key, value)
    }
    properties.foreach {
      case (key, value) => project.addProperty(key, value)
    }

    //  Compile Script and load compiled class
    try {
      script.compileAndExecute(project)
      projectPool.map { pool => pool.addProject(project) }
      project
    } catch {
      case e: InvocationTargetException =>
        Console.err.println("Errors in build script: " + projectFile)
        if (e.getCause != null) {
          throw e.getCause
        } else {
          throw e
        }
    }
  }

}

