package de.tototec.sbuild

object Constants {

  /**
   * The package(s) part of the plugins public API.
   * The packages will be part of the current projects classpath.
   * All other packages of that plugin will only be available to the plugin implementation itself.
   */
  val SBuildPluginExportPackage = "SBuild-PluginExportPackage"

  /**
   * The full qualified class name of the plugin implementation class, which must implement [[Plugin]].
   */
  val SBuildPluginClass = "SBuild-PluginClass"

}