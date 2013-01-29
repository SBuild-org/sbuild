package de.tototec.sbuild

import java.io.File

object Module {
  def apply(dirOrFile: String)(implicit _project: Project): Module = {
    _project.findOrCreateModule(dirOrFile)
    new Module(dirOrFile, _project)
  }
  @deprecated(message = "Use Modules() instead.", since = "0.3.2.9000")
  def apply(dirOrFiles: String*)(implicit _project: Project): Seq[Module] =
    dirOrFiles.map { dirOrFile => Module(dirOrFile) }
}

object Modules {
  def apply(dirOrFiles: String*)(implicit _project: Project): Seq[Module] =
    dirOrFiles.map { dirOrFile => Module(dirOrFile) }
}


class Module private[sbuild] (dirOrFile: String, project: Project) {
  def targetRef(targetRefName: String): TargetRef = TargetRef(dirOrFile + "::" + targetRefName)(project)
  def targetRefs(targetRefNames: String*): TargetRefs = targetRefNames.map(t => targetRef(t))
}