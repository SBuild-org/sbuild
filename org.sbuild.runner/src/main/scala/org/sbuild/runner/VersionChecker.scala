package org.sbuild.runner

import org.sbuild.internal.I18n
import org.sbuild.internal.OSGiVersion
import java.io.File
import org.sbuild.SBuildException
import org.sbuild.SBuildVersion
import org.sbuild.Plugin.PluginInfo

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
  def assertPluginVersion(pluginInfo: LoadablePluginInfo, buildScript: Option[File]): Unit = {
    pluginInfo.sbuildVersion match {
      case None => // nothing to check
      case Some(v) =>
        val osgiVersion = OSGiVersion.parseVersion(v)
        if (osgiVersion.compareTo(new OSGiVersion(SBuildVersion.osgiVersion)) > 0) {
          val msg = tr("The plugin(s) \"{0}\" in project \"{1}\" requires at least SBuild version: {2}", pluginInfo.pluginClasses.map(_.name).mkString(","), buildScript, v)
          val ex = new SBuildException(msg)
          ex.buildScript = buildScript
          throw ex
        }
    }

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

