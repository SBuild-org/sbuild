package org.sbuild.runner.bootstrap

import org.sbuild.Project
import org.sbuild.ScanSchemeHandler
import org.sbuild.SchemeHandler
import org.sbuild.SchemeResolverWithDependencies
import org.sbuild.plugins.http.HttpSchemeHandler
import org.sbuild.plugins.mvn.MvnSchemeHandler
import org.sbuild.plugins.unzip.UnzipSchemeHandler
import org.sbuild.Plugin
import org.sbuild.MapperSchemeHandler
import org.sbuild.TargetRefs
import org.sbuild.TargetContext

class SBuildBootstrap(implicit _project: Project) {

  SchemeHandler("scan", new ScanSchemeHandler())

  import org.sbuild.plugins.mvn._
  SchemeHandler("mvn", new MvnSchemeHandler())

  import org.sbuild.plugins.http._
  Plugin[Http]("http")

  import org.sbuild.plugins.unzip._
  Plugin[Unzip]("zip") configure {
    _.copy(regexCacheable = true)
  }

  import org.sbuild.plugins.sourcescheme._
  Plugin[SourceScheme]("source") configure {
    _.addMapping({
      case path if path.startsWith("mvn:") => path + ";classifier=sources"
    }: PartialFunction[String, String])
  }

  // Experimental!
  SchemeHandler("show-files", new SchemeHandler with SchemeResolverWithDependencies {
    def localPath(schemeContext: SchemeHandler.SchemeContext): String = s"phony:${schemeContext.fullName}"
    def dependsOn(schemeContext: SchemeHandler.SchemeContext): TargetRefs = schemeContext.path
    def resolve(schemeContext: SchemeHandler.SchemeContext, targetContext: TargetContext): Unit = {
      println(s"Files of target ${schemeContext.path}:\n${targetContext.fileDependencies.mkString("\n")}")
    }
  })

  // Experimental: replace by plugin
  //  SchemeHandler("source", new MapperSchemeHandler(
  //    pathTranslators = Seq("mvn" -> { path => path + ";classifier=sources" })
  //  ))

  // Experimental: replace by plugin
  //  SchemeHandler("javadoc", new MapperSchemeHandler(
  //    pathTranslators = Seq("mvn" -> { path => path + ";classifier=javadoc" })
  //  ))
}
