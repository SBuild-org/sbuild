package de.tototec.sbuild.natures.experimental

import de.tototec.sbuild.ProjectConfigurationException
import de.tototec.sbuild.TargetRefs
import de.tototec.sbuild.TargetRefs._
import de.tototec.sbuild.Pathes
import de.tototec.sbuild.Target
import de.tototec.sbuild.TargetContext
import de.tototec.sbuild.IfNotUpToDate
import de.tototec.sbuild.Path

trait JavaSourcesConfig {
  def javaSources_sources: Seq[String] = Seq("src/main/java")
  def javaSources_encoding: String = "UTF-8"
  def javaSources_source: Option[String] = None
}

trait CompileJavaConfig extends ClassesDirConfig with JavaSourcesConfig with OutputDirConfig with ProjectConfig {
  def compileJava_targetName: String = "compileJava"
  def compileJava_classpath: TargetRefs = TargetRefs()
  def compileJava_dependsOn: TargetRefs = new TargetRefs()
  def compileJava_extraSources: Seq[String] = Seq()
  def compileJava_target: String
}

trait Java6CompilerConfig extends CompileJavaConfig with JavaSourcesConfig {
  override def javaSources_source = Some("1.6")
  override def compileJava_target = "1.6"
}


trait CompileJavaNature extends Nature { this: CompileJavaConfig =>
  //  def compileJava_targetName: String = "compileJava"
  def compileJava_outputDir: String = classesDir
  //  def compileJava_classpath: TargetRefs = TargetRefs()
  //  def compileJava_dependsOn: TargetRefs = new TargetRefs()
  //  def compileJava_extraSources: Seq[String] = Seq()
  //  def compileJava_target: String

  abstract override def createTargets: Seq[Target] = {

    val sources = Pathes(javaSources_sources ++ compileJava_extraSources)

    val compile = Target("phony:" + compileJava_targetName) dependsOn
      compileJava_classpath ~ compileJava_dependsOn exec { ctx: TargetContext =>
        IfNotUpToDate(sources ++ ctx.fileDependencies, Path(outputDir), ctx) {
          import de.tototec.sbuild.ant._
          import de.tototec.sbuild.ant.tasks._

          AntMkdir(dir = Path(compileJava_outputDir))
          AntJavac(
            // TODO: add more
            source = javaSources_source.getOrElse(null),
            target = compileJava_target,
            encoding = javaSources_encoding,
            classpath = AntPath(locations = ctx.fileDependencies),
            destDir = Path(compileJava_outputDir),
            srcDir = AntPath(locations = sources.filter(_.exists))
          )
        }
      }

    super.createTargets ++ Seq(compile)
  }
}

