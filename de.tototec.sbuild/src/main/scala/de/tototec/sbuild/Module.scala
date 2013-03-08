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

class Module private[sbuild] (dirOrFile: String, project: Project) {
  def name = dirOrFile
  def apply(targetName: String): TargetRef = targetRef(targetName)
  def targetRef(targetName: String): TargetRef = TargetRef(dirOrFile + "::" + targetName)(project)
  def targetRefs(targetNames: String*): TargetRefs = targetNames.map(t => targetRef(t))
  // def listTargets: Seq[TargetRef] = project.targets.values.toSeq.map { t => TargetRef(t) }
}

object Modules {
  def apply(dirOrFiles: String*)(implicit _project: Project): Seq[Module] =
    dirOrFiles.map { dirOrFile => Module(dirOrFile) }
}

//class Modules(modules: Seq[Module]) {
//  def targetRefs(targetRefName: String): TargetRefs = TargetRefs(modules.map(_ targetRef targetRefName))
//}