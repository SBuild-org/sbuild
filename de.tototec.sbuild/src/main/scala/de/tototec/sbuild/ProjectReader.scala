package de.tototec.sbuild

import java.io.File

trait ProjectReader {
  def readProject(projectToRead: Project, projectFile: File): Any
}