package de.tototec.sbuild.ant

import de.tototec.sbuild.Project
import org.apache.tools.ant.BuildListener
import org.apache.tools.ant.BuildEvent

object AntProject {
  private var antProjects: List[(Project, AntProject)] = List()
  def apply()(implicit project: Project): AntProject = antProjects.find {
    case (p, _) => p.eq(project)
  } match {
    case Some((_, p)) => p
    case None =>
      val p = new AntProject(project)
      antProjects ::= (project -> p)
      p
  }

  //  implicit def fromProject(implicit project: Project): AntProject = AntProject(project)
}

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

  setBaseDir(project.projectDirectory.jfile)
  setJavaVersionProperty()
  setSystemProperties()

}