package de.tototec.sbuild

import java.io.File

trait ProjectReader {
  def readAndCreateProject(projectFile: File, properties: Map[String, String], projectPool: Option[ProjectPool], log: Option[SBuildLogger]): Project
}