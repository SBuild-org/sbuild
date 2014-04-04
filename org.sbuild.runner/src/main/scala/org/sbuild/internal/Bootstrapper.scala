package org.sbuild.internal

import org.sbuild.Project

trait Bootstrapper {
  def applyToProject(project: Project) = {}
}

object Bootstrapper {

  val Empty = new Bootstrapper {}

  // TODO: Run it with the project classpath
  //  val Default = new Bootstrapper {
  //    override def applyToProject(project: Project) = {
  //      import org.sbuild.plugins.http._
  //      import org.sbuild.plugins.mvn._
  //      import org.sbuild.plugins.unzip._
  //
  //      project match {
  //        case pluginProj: PluginAwareImpl =>
  //          pluginProj.registerPlugin(classOf[Unzip].getName(), classOf[UnzipPlugin].getName(), SBuildVersion.version, getClass().getClassLoader())
  //      }
  //
  //      implicit val p = project
  //      SchemeHandler("http", new HttpSchemeHandler())
  //      SchemeHandler("mvn", new MvnSchemeHandler())
  //      Plugin[Unzip]("zip")
  //      SchemeHandler("scan", new ScanSchemeHandler())
  //
  //      // Experimental
  //
  //      SchemeHandler("source", new MapperSchemeHandler(
  //        pathTranslators = Seq("mvn" -> { path => path + ";classifier=sources" })
  //      ))
  //      SchemeHandler("javadoc", new MapperSchemeHandler(
  //        pathTranslators = Seq("mvn" -> { path => path + ";classifier=javadoc" })
  //      ))
  //    }
  //  }

}