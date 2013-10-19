package de.tototec.sbuild

import java.io.File

trait ProjectReader {
  /**
   * Read a project file and create a configured Project instance associated by that file.
   * 
   * If a projectPool is given, the newly created project will be added to that pool.
   *
   */
  def readAndCreateProject(projectFile: File, properties: Map[String, String], projectPool: Option[ProjectPool], monitor: Option[CmdlineMonitor]): Project
}