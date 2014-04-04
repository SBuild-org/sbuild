package org.sbuild.runner.bootstrap

import org.sbuild.Project
import org.sbuild.ScanSchemeHandler
import org.sbuild.SchemeHandler
import org.sbuild.plugins.http.HttpSchemeHandler
import org.sbuild.plugins.mvn.MvnSchemeHandler
import org.sbuild.plugins.unzip.UnzipSchemeHandler
import org.sbuild.Plugin
import org.sbuild.plugins.http.HttpSchemeHandler
import org.sbuild.plugins.mvn.MvnSchemeHandler
import org.sbuild.MapperSchemeHandler
import org.sbuild.plugins.http.HttpSchemeHandler
import org.sbuild.plugins.mvn.MvnSchemeHandler

class SBuildBootstrap(implicit _project: Project) {

  SchemeHandler("scan", new ScanSchemeHandler())

  import org.sbuild.plugins.mvn._
  SchemeHandler("mvn", new MvnSchemeHandler())

  import org.sbuild.plugins.http._
  SchemeHandler("http", new HttpSchemeHandler())

  import org.sbuild.plugins.unzip._
  Plugin[Unzip]("zip") configure {
    _.copy(regexCacheable = true)
  }

  // Experimental: replace by plugin
  SchemeHandler("source", new MapperSchemeHandler(
    pathTranslators = Seq("mvn" -> { path => path + ";classifier=sources" })
  ))

  // Experimental: replace by plugin
  SchemeHandler("javadoc", new MapperSchemeHandler(
    pathTranslators = Seq("mvn" -> { path => path + ";classifier=javadoc" })
  ))
}
