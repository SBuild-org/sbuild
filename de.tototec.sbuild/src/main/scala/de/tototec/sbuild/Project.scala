package de.tototec.sbuild

class Project {

  var targets = List[Target]()

  def findTarget(name: String): Option[Target] = {
    val parsed = Target.parseTargetName(name)
    targets.find(g => g.name == name || g.filePath == "phony:" + name || g.filePath == parsed.filePath)
  }

  def findTarget(targetRef: TargetRef): Option[Target] = targetRef match {
    case target: Target => Some(target)
    case _ => {
      val parsed = Target.parseTargetName(targetRef.name)
      targets find { t =>
        t.name == targetRef.name ||
          t.filePath == "phony:" + targetRef.name ||
          t.filePath == parsed.filePath
      }
    }
  }

  def prerequisites(target: Target) = target.dependants.map(dep => findTarget(dep) match {
    case Some(target) => target
    case None => throw new ProjectConfigurationException("Non-existing dependency '" + dep.name + "' found in goal: " + target)
  }).toList

  def prerequisitesMap: Map[Target, List[Target]] = targets.map(goal => (goal, prerequisites(goal))).toMap

}