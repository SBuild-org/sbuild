package de.tototec.sbuild

import java.io.File

object Module {
  def apply(dirOrFile: String)(implicit _project: Project): Module = {
    _project.findOrCreateModule(dirOrFile, copyProperties = false)
    new Module(dirOrFile, _project)
  }
}

class Module private (dirOrFile: String, project: Project) {
  def name = dirOrFile
  def apply(targetName: String): TargetRef = targetRef(targetName)
  def targetRef(targetName: String): TargetRef = TargetRef(dirOrFile + "::" + targetName)(project)
  def targetRefs(targetNames: String*): TargetRefs = targetNames.map(t => targetRef(t))
}

object Modules {
  def apply(dirOrFiles: String*)(implicit _project: Project): Seq[Module] =
    dirOrFiles.map { dirOrFile => Module(dirOrFile) }
}

