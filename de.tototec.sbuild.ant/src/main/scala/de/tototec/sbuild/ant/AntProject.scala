package de.tototec.sbuild.ant

import de.tototec.sbuild.Project
import org.apache.tools.ant.BuildListener
import org.apache.tools.ant.BuildEvent
import de.tototec.sbuild.Path
import java.io.File

/**
 * Companion object for [[AntProject]],
 * SBuild implementation to be used in conjunction with Ant specific API like Ant tasks.
 *
 * You can use [[AntProject$.apply]] at any point in your SBuild project, to get the Ant project.
 */
object AntProject {
  /**
   * Get or create an Ant project implementation, that is associated to the SBuild project given with the implicit parameter `project`.
   */
  def apply()(implicit project: Project): AntProject = project.antProject match {
    case Some(p: AntProject) => p
    case _ =>
      val p = new AntProject(project)
      project.antProject = Some(p)
      p
  }
}

/**
 * SBuild specific implementation of Ant's project ([[org.apache.tools.ant.Project]]).
 *
 * To use Ant task in SBuild, one needs to provide an instance of an Ant project.
 * The preferred way to get an SBuild aware Ant project is [[AntProject$.apply]].
 *
 * The underlying Ant project will be configured to the same project directory and will log in the INFO log level.
 * You can change the log level through the system property "`ant.loglevel`".
 *
 */
class AntProject(project: Project) extends org.apache.tools.ant.Project {

  addBuildListener(new BuildListener() {
    override def messageLogged(buildEvent: BuildEvent) {
      if (buildEvent.getPriority <= project.properties.getOrElse("ant.loglevel", org.apache.tools.ant.Project.MSG_INFO.toString).toInt) {
        Console.println(buildEvent.getMessage)
        buildEvent.getException match {
          case null =>
          case t => Console.println(t.toString)
        }
      }
    }
    override def buildStarted(buildEvent: BuildEvent) {}
    override def buildFinished(buildEvent: BuildEvent) {}
    override def targetStarted(buildEvent: BuildEvent) {}
    override def targetFinished(buildEvent: BuildEvent) {}
    override def taskFinished(buildEvent: BuildEvent) {}
    override def taskStarted(buildEvent: BuildEvent) {}
  })

  setBaseDir(project.projectDirectory)
  setJavaVersionProperty()
  setSystemProperties()

}
