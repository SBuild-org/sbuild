package de.tototec.sbuild.runner

import de.tototec.sbuild.internal.I18n
import de.tototec.sbuild.internal.OSGiVersion
import java.io.File
import de.tototec.sbuild.SBuildException
import de.tototec.sbuild.SBuildVersion
import de.tototec.sbuild.Plugin.PluginInfo

object VersionChecker {
  case class BuildscriptOrPlugin(buildscript: File, plugin: Option[String] = None)
}

class VersionChecker {
  import VersionChecker._

  private[this] val i18n = I18n[VersionChecker]
  import i18n._

  def assertBuildscriptVersion(requestedVersion: String, buildScript: File): Unit = {
    val osgiVersion = OSGiVersion.parseVersion(requestedVersion)
    if (osgiVersion.compareTo(new OSGiVersion(SBuildVersion.osgiVersion)) > 0) {
      val msg = tr("The buildscript \"{0}\" requires at least SBuild version: {1}", buildScript, requestedVersion)
      val ex = new SBuildException(msg)
      ex.buildScript = Some(buildScript)
      throw ex
    }
  }

  // TODO: also detect, if a plugin required a higher version that declared in the buildfile.
  def assertPluginVersion(pluginInfo: PluginInfo): Unit = {

  }

  def checkVersion(requestedVersion: String, assert: Option[BuildscriptOrPlugin] = None): Boolean = {
    val osgiVersion = OSGiVersion.parseVersion(requestedVersion)
    if (osgiVersion.compareTo(new OSGiVersion(SBuildVersion.osgiVersion)) > 0) {
      assert map {
        case a if a.plugin.isDefined =>
          tr("The plugin \"{0}\" in buildscript \"{1}\" requires at least SBuild version: {2}", a.plugin.get, a.buildscript, requestedVersion)
        case a =>
          tr("The buildscript \"{0}\" requires at least SBuild version: {1}", a.buildscript, requestedVersion)
      } map { msg =>
        val ex = new SBuildException(msg)
        ex.buildScript = Some(assert.get.buildscript)
        throw ex
      }
      false
    } else true
  }

}

