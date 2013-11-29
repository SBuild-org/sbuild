package de.tototec.sbuild.plugins.jar

import java.io.File
import de.tototec.sbuild.Path
import de.tototec.sbuild.Plugin
import de.tototec.sbuild.Project
import de.tototec.sbuild.Target
import de.tototec.sbuild.TargetRef.fromString
import de.tototec.sbuild.TargetRefs.fromTarget
import de.tototec.sbuild.toRichFile
import de.tototec.sbuild.CmdlineMonitor
import de.tototec.sbuild.TargetRefs
import de.tototec.sbuild.ant.tasks.AntJar

class Jar(val name: String)(implicit project: Project) {
  var jarName: String = "classes.jar"
  var destDir: File = Path("target")
  var aggregatorTarget: Option[String] = Some("phony:jar")
  var dependsOn: TargetRefs = "compile"
  var baseDir: File = Path("target/classes")
  var deleteExitingJar: Boolean = true
  // TODO: support file sets
  // var resources: 
}

class JarPlugin(implicit project: Project) extends Plugin[Jar] {

  def create(name: String): Jar = new Jar(name)

  def applyToProject(instances: Seq[(String, Jar)]): Unit = {
    instances.foreach {
      case (name, jar) =>

        val jarFile = jar.destDir / jar.jarName
        val watchFiles = s"scan:${jar.baseDir}"

        val jarTarget = Target(jarFile) dependsOn jar.dependsOn ~~ watchFiles exec {
          if (jar.deleteExitingJar) jarFile.deleteFile
          AntJar(destFile = jarFile, baseDir = jar.baseDir)
        }

        jar.aggregatorTarget.map { t =>
          Target(t) dependsOn jarTarget
        }
    }
  }

}