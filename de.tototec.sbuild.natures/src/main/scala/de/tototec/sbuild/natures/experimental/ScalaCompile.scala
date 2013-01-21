package de.tototec.sbuild.natures.experimental

import de.tototec.sbuild._
import de.tototec.sbuild.TargetRefs._
import de.tototec.sbuild.ant.tasks._

trait ScalaSourcesConfig {
  def scalaSources_trigger: TargetRefs = TargetRefs()
  def scalaSources_sources: Seq[String] = Seq("src/main/scala", "src/main/java")
  def scalaSources_encoding: String = "UTF-8"
}

trait ScalaCompileConfig extends ScalaSourcesConfig with ClassesDirConfig with OutputDirConfig with ProjectConfig {
  def scalaCompile_targetName: String = "compileScala"
  def scalaCompile_outputDir: String = classesDir
  def scalaCompile_dependsOn: TargetRefs = TargetRefs()
  def scalaCompile_extraSources: Seq[String] = Seq()
  def scalaCompile_debugInfo: String = "vars"
  def scalaCompile_target: Option[String] = None
  def scalaCompile_deprecation: Boolean = true
  def scalaCompile_uncecked: Boolean = true
  def scalaCompile_classpath: TargetRefs = TargetRefs()
  def scalaCompile_scalaVersion: Option[String] = None
  def scalaCompile_compilerClasspath: TargetRefs = {

    val Scala27 = """(2\.7\..*)""".r
    val Scala28 = """(2\.8\..*)""".r
    val Scala29 = """(2\.9\..*)""".r
    val Scala210 = """(2\.10\..*)""".r
    val Scala211 = """(2\.11\..*)""".r

    scalaCompile_scalaVersion match {
      case None =>
        val ex = new ProjectConfigurationException(s"CompileScalaNature: Unspecified Scala version.")
        ex.buildScript = Option(project.projectFile)
        throw ex
      case Some(scalaVersion) => scalaVersion match {
        case Scala27(_) | Scala28(_) | Scala29(_) =>
          s"mvn:org.scala-lang:scala-library:${scalaVersion}" ~
            s"mvn:org.scala-lang:scala-compiler:${scalaVersion}"
        case Scala210(v) =>
          s"mvn:org.scala-lang:scala-library:${scalaVersion}" ~
            s"mvn:org.scala-lang:scala-compiler:${scalaVersion}" ~
            s"mvn:org.scala-lang:scala-reflect:${scalaVersion}"
        case Scala211(v) =>
          // not exactly sure about future compiler classpath and sources
          s"mvn:org.scala-lang:scala-library:${scalaVersion}" ~
            s"mvn:org.scala-lang:scala-compiler:${scalaVersion}" ~
            s"mvn:org.scala-lang:scala-reflect:${scalaVersion}"
        case _ =>
          val ex = new ProjectConfigurationException(s"CompileScalaNature: Unsupported Scala version ${scalaVersion} specified.")
          ex.buildScript = Option(project.projectFile)
          throw ex
      }
    }
  }
}

trait ScalaCompileNature extends Nature { this: ScalaCompileConfig =>

  abstract override def createTargets: Seq[Target] = {

    val compilerClasspath = scalaCompile_compilerClasspath

    super.createTargets ++
      Seq(
        Target("phony:" + scalaCompile_targetName) dependsOn
          compilerClasspath ~ scalaCompile_classpath ~ scalaCompile_dependsOn exec {
            ctx: TargetContext =>

              val sources = Pathes(scalaSources_sources ++ scalaCompile_extraSources)

              IfNotUpToDate(sources ++ ctx.fileDependencies, Path(outputDir), ctx) {
                AntMkdir(dir = Path(scalaCompile_outputDir))
                addons.scala.Scalac(
                  target = scalaCompile_target.getOrElse(null),
                  encoding = scalaSources_encoding,
                  deprecation = scalaCompile_deprecation,
                  unchecked = scalaCompile_uncecked,
                  debugInfo = scalaCompile_debugInfo,
                  fork = true,
                  destDir = Path(scalaCompile_outputDir),
                  srcDirs = sources,
                  compilerClasspath = compilerClasspath.files,
                  classpath = scalaCompile_classpath.files
                )
              }

          }
      )
  }
}