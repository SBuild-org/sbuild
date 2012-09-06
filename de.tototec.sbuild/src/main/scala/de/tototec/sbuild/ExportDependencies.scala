package de.tototec.sbuild

object ExportDependencies {

  def apply(exportName: String, dependencies: TargetRefs)(implicit project: Project) {
    def depAsXml(dep: TargetRef) =
      "<dep><![CDATA[" +
        (if (dep.explicitProject.isDefined) (dep.explicitProject + "::") else "") +
        dep.name +
        "]]></dep>"

    project.addProperty(exportName, dependencies.targetRefs.map(depAsXml(_)).mkString("<deps>", "", "</deps>"))
  }

}