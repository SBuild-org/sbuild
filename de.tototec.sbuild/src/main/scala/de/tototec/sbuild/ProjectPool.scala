package de.tototec.sbuild

import java.io.File

class ProjectPool(val baseProject: Project) {
  private var _projects: Map[File, Project] = Map()
  addProject(baseProject)

  def addProject(project: Project) {
    _projects += (project.projectFile -> project)
  }

  def projects: Seq[Project] = _projects.values.toSeq
  def propjectMap: Map[File, Project] = _projects

  def formatProjectFileRelativeToBase(project: Project): String =
    if (baseProject != project)
      baseProject.projectDirectory.toURI.relativize(project.projectFile.toURI).getPath
    else project.projectFile.getName

}
