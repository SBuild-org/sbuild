package de.tototec.sbuild.brainstorm
import scala.collection.mutable.ListBuffer

class Brainstorm {

}

case class ExecResult(changed: Boolean)

case class TaskHandle(dependsOn: List[TaskHandle])

case class TaskRef(name: String)

object Task {
  def apply(name: String)(implicit project: Project): Task = {
    project.findOrCreateTask(name)
  }
}

trait Task {
  def exec(exec: => Unit): Task = {
    action(() => {
      exec
      ExecResult(true)
    })
  }
  def action(action: () => ExecResult): Task
  def dependsOn(task: TaskRef*): Task
}

class TaskImpl(val name: String) extends Task {
  private var action: () => ExecResult = _
  override def action(action: () => ExecResult): Task = {
    this.action = action
    this
  }
  private var dependsOn = Seq[TaskRef]()
  override def dependsOn(tasks: TaskRef*): Task = {
    this.dependsOn = tasks
    this
  }
}

class Project {
  private var tasks = ListBuffer[TaskImpl]()

  def findOrCreateTask(name: String): Task = {
    tasks.find(t => t.name == name) match {
      case Some(found) => found
      case None => {
        val taskImpl = new TaskImpl(name)
        tasks.append(taskImpl)
        taskImpl
      }
    }
  }
}

