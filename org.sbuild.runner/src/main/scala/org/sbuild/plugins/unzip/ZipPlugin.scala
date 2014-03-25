package org.sbuild.plugins.unzip

import org.sbuild.Plugin
import org.sbuild.Path
import org.sbuild.SchemeHandler
import org.sbuild.Project

class ZipPlugin(implicit project: Project) extends Plugin[Zip] {

  def create(name: String): Zip = {
    val schemeName = if (name == "") "zip" else name
    val baseDir = Path(s".sbuild/${schemeName}")
    Zip(schemeName = schemeName,
      baseDir = baseDir)
  }

  def applyToProject(instances: Seq[(String, Zip)]): Unit = instances foreach {
    case (name, zip) =>
      SchemeHandler(zip.schemeName, new ZipSchemeHandler(zip.baseDir, zip.regexCacheable))
  }

}