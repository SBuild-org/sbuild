package de.tototec.sbuild

object Constants {

  /**
   * The package(s) part of the plugins public API.
   * The packages will be part of the current projects classpath.
   * All other packages of that plugin will only be available to the plugin implementation itself.
   */
  val SBuildPluginExportPackage = "SBuild-ExportPackage"

  /**
   * A pair of full qualified class names denoting the plugin instance class and it factory delimited by an equals (=) sign. The factory class must implement [[Plugin]].
   */
  val SBuildPlugin = "SBuild-Plugin"

  /**
   * Addional classpath which will be automatically resolved and used when this header is present in a plugin JAR.
   */
  val SBuildPluginClasspath = "SBuild-Classpath"

}