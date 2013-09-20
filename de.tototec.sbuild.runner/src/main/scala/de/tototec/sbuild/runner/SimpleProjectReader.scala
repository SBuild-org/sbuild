package de.tototec.sbuild.runner

import de.tototec.sbuild.Project
import java.io.File
import java.lang.reflect.InvocationTargetException
import de.tototec.sbuild.ProjectReader
import de.tototec.sbuild.SBuildLogger
import de.tototec.sbuild.SBuildNoneLogger
import de.tototec.sbuild.BuildFileProject
import de.tototec.sbuild.ProjectPool

class SimpleProjectReader(
  classpathConfig: ClasspathConfig,
  log: SBuildLogger = SBuildNoneLogger,
  clean: Boolean = false,
  fileLocker: FileLocker)
    extends ProjectReader {

  override def readAndCreateProject(projectFile: File, properties: Map[String, String], projectPool: Option[ProjectPool], log: Option[SBuildLogger]): Project = {
    val script = new ProjectScript(projectFile, classpathConfig, log.getOrElse(this.log), fileLocker)
    if (clean) {
      script.clean
    }

    val project = new BuildFileProject(projectFile, this, projectPool, Some(script.typesToIncludedFilesPropertiesFile), log = log.getOrElse(this.log))

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

