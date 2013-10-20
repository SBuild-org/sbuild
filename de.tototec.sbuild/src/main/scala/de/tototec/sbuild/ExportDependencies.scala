package de.tototec.sbuild

/**
 * Export dependencies to be consumed by other tools, e.g. an IDE.
 *
 * This is e.g. used by the SBuild Eclipse Plugin to refer the dependencies that should be part of the Eclipse Classpath Container.
 *
 * Example that exports two dependencies under the name "eclipse.classpath":
 * {{{
 * val compileCp =
 *   "mvn:org.slf4j:slf4j-api:1.7.5" ~
 *   "mvn:de.tototec:de.tototec.cmdoption:0.3.0"
 *
 * ExportDependencies("eclipse.classpath", compileCp)
 * }}}
 *
 * @since 0.1.1
 *
 */
object ExportDependencies {

  private[this] val log = Logger[ExportDependencies.type]

  /**
   * Export dependencies to be consumed by other tools, e.g. an IDE.
   *
   * @param exportName The name that the external tool can use to refer to the exported dependencies.
   * @param dependencies The dependencies ([[TargetRefs]]) to be exported.
   *   Most typically, the compile, rutime or test classpath dependencies.
   *
   */
  def apply(exportName: String, dependencies: TargetRefs)(implicit project: Project) {
    def depAsXml(dep: TargetRef) =
      "<dep><![CDATA[" +
        (if (dep.explicitProject.isDefined) (dep.explicitProject + "::") else "") +
        dep.name +
        "]]></dep>"

    log.debug("About to export dependencies under the property \"" + exportName + "\" in project: " + project.projectFile + ". Dependencies: " + dependencies)
    project.addProperty(exportName, dependencies.targetRefs.map(depAsXml(_)).mkString("<deps>", "", "</deps>"))
  }

}