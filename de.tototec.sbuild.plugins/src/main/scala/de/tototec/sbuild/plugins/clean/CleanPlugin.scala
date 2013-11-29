package de.tototec.sbuild.plugins.clean

import java.io.File
import de.tototec.sbuild.Path
import de.tototec.sbuild.Plugin
import de.tototec.sbuild.Project
import de.tototec.sbuild.Target
import de.tototec.sbuild.TargetRef.fromString
import de.tototec.sbuild.TargetRefs.fromTarget
import de.tototec.sbuild.toRichFile
import de.tototec.sbuild.CmdlineMonitor

class Clean(val name: String)(implicit project: Project) {
  var targetName = if (name == "") "clean" else s"clean-$name"
  var dirs: Seq[File] = Seq(Path(if (name == "") "target" else name))
  var evictCache: Boolean = true
}

class CleanPlugin(implicit project: Project) extends Plugin[Clean] {

  def create(name: String): Clean = new Clean(name)

  def applyToProject(instances: Seq[(String, Clean)]): Unit = {
    instances.foreach {
      case (name, clean) =>
        val cleanTarget = Target(s"phony:${clean.targetName}") exec {
          clean.dirs.foreach { dir =>
            if (dir.exists()) {
              project.monitor.info(CmdlineMonitor.Verbose, "Deleting " + dir)
            }
            dir.deleteRecursive
          }
        }
        if (clean.evictCache) cleanTarget.evictCache
        if (clean.targetName != "clean") Target("phony:clean") dependsOn cleanTarget
    }
  }

}