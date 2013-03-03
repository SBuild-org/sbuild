package de.tototec.sbuild.test

import java.io.File
import de.tototec.sbuild.Project

object TestSupport {

  def createProjectFile: File = {
    val tmpFile = File.createTempFile("SBuild", ".scala")
    tmpFile.deleteOnExit
    tmpFile
  }

  def createMainProject: Project = {
    new Project(createProjectFile)
  }

}
