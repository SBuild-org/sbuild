package de.tototec.sbuild.plugins.javac

import java.io.File
import de.tototec.sbuild.Path
import de.tototec.sbuild.Plugin
import de.tototec.sbuild.Project
import de.tototec.sbuild.toRichFile
import de.tototec.sbuild.addons.java.{ Javac => JavacAddon }
import de.tototec.sbuild.Target
import de.tototec.sbuild.TargetRefs
import de.tototec.sbuild.TargetRef
import de.tototec.sbuild.TargetContext

class Javac(val name: String)(implicit project: Project) {
  var targetName: String = if (name == "") "compile" else s"compile-$name"
  var classpath: TargetRefs = TargetRefs()
  var targetDir: File = Path("target") / (if (name == "") "" else s"$name-" + "classes")
  //  var sources: TargetRefs
  var compilerClasspath: Option[TargetRefs] = None
  var sources: Option[TargetRefs] = None
  var srcDirs: Seq[File] = Seq(Path("src") / (if (name == "") "main" else name) / "java")
  var destDir: File = Path("target") / (if (name == "") "classes" else s"$name-classes")
  var encoding: String = "UTF-8"
  var deprecation: Option[Boolean] = None
  var verbose: Option[Boolean] = None
  var source: Option[String] = None
  var target: Option[String] = None
  var debugInfo: Option[String] = None
  var fork: Boolean = false
  var additionalJavacArgs: Seq[String] = Seq()
}

class JavacPlugin(implicit project: Project) extends Plugin[Javac] {

  def create(name: String): Javac = new Javac(name)

  def applyToProject(instances: Seq[(String, Javac)]): Unit = {
    instances.foreach {
      case (name, javac) =>

        val sources: TargetRefs = javac.sources.getOrElse(javac.srcDirs.map(dir => TargetRef(s"scan:$dir;regex=.*\\.java")))
        val compilerClasspath: TargetRefs = javac.compilerClasspath.getOrElse(TargetRefs())
        val dependencies: TargetRefs = compilerClasspath ~ javac.classpath ~~ sources

        Target(s"phony:${javac.targetName}").cacheable dependsOn dependencies exec { ctx: TargetContext =>

          if (sources.files.isEmpty) {
            // project.monitor.warn("No sources files found.")
            // ctx.error("No source files found.")
            throw new RuntimeException("No source files found.")
          }

          val compiler = new JavacAddon(
            classpath = javac.classpath.files,
            sources = sources.files,
            destDir = javac.destDir,
            encoding = javac.encoding,
            fork = javac.fork,
            additionalJavacArgs = javac.additionalJavacArgs
          )

          javac.compilerClasspath.map { cp => compiler.compilerClasspath = cp.files }
          javac.deprecation.map { d => compiler.deprecation = d }
          javac.verbose.map { d => compiler.verbose = d }
          javac.source.map { d => compiler.source = d }
          javac.target.map { d => compiler.target = d }
          javac.debugInfo.map { d => compiler.debugInfo = d }

          compiler.execute
        }
    }
  }

}