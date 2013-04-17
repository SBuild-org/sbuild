package de.tototec.sbuild.test

import java.io.File
import de.tototec.sbuild.Project
import de.tototec.sbuild.BuildFileProject

object TestSupport {

  def createProjectFile: File = {
    val tmpFile = File.createTempFile("SBuild", ".scala")
    tmpFile.deleteOnExit
    tmpFile
  }

  def createMainProject: Project = {
    new BuildFileProject(createProjectFile)
  }

}
