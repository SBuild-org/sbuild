package de.tototec.sbuild.runner

import de.tototec.sbuild.Project
import java.io.File
import java.lang.reflect.InvocationTargetException
import de.tototec.sbuild.ProjectReader
import de.tototec.sbuild.SBuildLogger
import de.tototec.sbuild.SBuildNoneLogger

class SimpleProjectReader(config: Config, classpathConfig: ClasspathConfig, log: SBuildLogger = SBuildNoneLogger) extends ProjectReader {
  val downloadCache: DownloadCache = new SimpleDownloadCache()
  
  override def readProject(projectToRead: Project, projectFile: File): Any = {
    val script = new ProjectScript(projectFile, classpathConfig, Some(downloadCache), log)
    if (config.clean) {
      script.clean
    }
    //  Compile Script and load compiled class
    try {
      script.compileAndExecute(projectToRead)
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