package de.tototec.sbuild.test

import java.io.File
import de.tototec.sbuild.Project
import de.tototec.sbuild.BuildFileProject
import java.io.FileOutputStream
import java.io.BufferedOutputStream
import java.io.PrintStream
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

  def createMainProject(content: String): Project = {
    val file = createProjectFile
    val stream = new PrintStream(new BufferedOutputStream(new FileOutputStream(file)))
    stream.print(content)
    stream.close()

    new BuildFileProject(file)
  }

}
