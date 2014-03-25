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

class SimpleProjectReader(
  classpathConfig: ClasspathConfig,
  monitor: CmdlineMonitor = NoopCmdlineMonitor,
  clean: Boolean = false,
  fileLocker: FileLocker,
  initialProperties: Map[String, String] = Map())
    extends ProjectReader {

  override def readAndCreateProject(projectFile: File, properties: Map[String, String], projectPool: Option[ProjectPool], monitor: Option[CmdlineMonitor]): Project = {
    val projectMonitor = monitor.getOrElse(this.monitor)

    val bootstrapper = new Bootstrapper {
      override def applyToProject(project: Project) = {
        import org.sbuild.plugins.http._
        import org.sbuild.plugins.mvn._
        import org.sbuild.plugins.unzip._

        project match {
          case pluginProj: PluginAwareImpl =>
            pluginProj.registerPlugin(classOf[Zip].getName(), classOf[ZipPlugin].getName(), SBuildVersion.version, getClass().getClassLoader())
        }

        implicit val p = project
        SchemeHandler("http", new HttpSchemeHandler())
        SchemeHandler("mvn", new MvnSchemeHandler())
        Plugin[Zip]("zip")
        SchemeHandler("scan", new ScanSchemeHandler())

        // Experimental

        SchemeHandler("source", new MapperSchemeHandler(
          pathTranslators = Seq("mvn" -> { path => path + ";classifier=sources" })
        ))
        SchemeHandler("javadoc", new MapperSchemeHandler(
          pathTranslators = Seq("mvn" -> { path => path + ";classifier=javadoc" })
        ))
      }
    }

    val script = new ProjectScript(projectFile, classpathConfig, projectMonitor, fileLocker)
    if (clean) {
      script.clean
    }

    val project = new BuildFileProject(projectFile, this, projectPool, Some(script.typesToIncludedFilesPropertiesFile), monitor = projectMonitor)

    initialProperties.foreach {
      case (key, value) => project.addProperty(key, value)
    }
    properties.foreach {
      case (key, value) => project.addProperty(key, value)
    }

    bootstrapper.applyToProject(project)

    //  Compile Script and load compiled class
    try {
      val compiledScript = script.resolveAndCompile(bootstrapper)
      compiledScript.applyToProject(project)
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

}

