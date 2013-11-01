import de.tototec.sbuild._
import de.tototec.sbuild.ant._
import de.tototec.sbuild.ant.tasks._
import de.tototec.sbuild.TargetRefs._

@version("0.4.0")
@include("../SBuildConfig.scala")
@classpath(
  "mvn:org.apache.ant:ant:1.8.4",
  "mvn:org.asciidoctor:asciidoctor-java-integration:0.1.4",
  "mvn:org.jruby:jruby-complete:1.7.4"
)
class SBuild(implicit _project: Project) {

  Target("phony:clean").evictCache exec {
    AntDelete(dir = Path("target"))
    // Path("target").deleteRecursive
  }

  Target("phony:all") dependsOn "manpage" ~ "manual"

  val jBakeDeps = Seq(
    "jbake.jar",
    "lib/args4j-2.0.23.jar",
    "lib/commons-configuration-1.9.jar",
    "lib/commons-io-2.4.jar",
    "lib/commons-lang-2.6.jar",
    "lib/commons-logging-1.1.1.jar",
    "lib/freemarker-2.3.19.jar",
    "lib/markdownj-0.3.0-1.0.2b4.jar"
  ).map(file => TargetRef(s"zip:file=jbake-2.1/${file};archive=http://jbake.org/files/jbake-2.1-bin.zip"))

  Target("phony:experimentalJBake") dependsOn jBakeDeps exec {
    val sourceDir = Path("jbake")
    val targetDir = Path("target/jbake")
    addons.support.ForkSupport.runJavaAndWait(classpath = jBakeDeps.files, arguments = Array(
      "org.jbake.launcher.Main", sourceDir.getAbsolutePath, targetDir.getAbsolutePath
    ))
  }

  val asciidoctor = "mvn:org.asciidoctor:asciidoctor-java-integration:0.1.4"
  val jruby = "mvn:org.jruby:jruby-complete:1.7.4"
  val manual = "src/manual/SBuildManual.adoc"
  val manpage = "src/manpage/sbuild.1.adoc"

  Target("phony:manpage") dependsOn "manpage-native"

  Target("phony:manpage-asciidoctor-fork") dependsOn manpage ~ SBuildConfig.scalaLibrary ~ asciidoctor ~ "scan:.sbuild/scala/SBuild.scala" ~ jruby exec { ctx: TargetContext =>
    addons.support.ForkSupport.runJavaAndWait(
      classpath = (SBuildConfig.scalaLibrary ~ asciidoctor ~ jruby).files ++ Seq(Path(".sbuild/scala/SBuild.scala")),
      arguments = Array(
        "AsciiDoctorRunner",
        "backend=html5",
        "targetDir=" + Path("target").getPath,
        manpage.files.head.getPath)
    )
  }

  Target("phony:manual") dependsOn "manual-native"

  Target("phony:manpage-native").cacheable dependsOn manpage ~ "scan:/src/manpage" exec {
    val targetDir = Path("target/manpage")
    targetDir.mkdirs
    val result = addons.support.ForkSupport.runAndWait(
      Array("a2x", "-v", "-D", targetDir.getPath, "--format", "manpage", Path(manpage).getPath, "-a", "revversion=" + SBuildConfig.sbuildVersion)
    )

    if(result != 0) throw new RuntimeException("a2x failed. Return code: " + result)
  }

  Target("phony:manual-native").cacheable dependsOn manual ~ "scan:src/manual" exec {
    val targetDir = Path("target/manual")
    targetDir.mkdirs
    val result = addons.support.ForkSupport.runAndWait(
      Array("a2x", "-v", "-D", targetDir.getPath, "--format", "xhtml", Path(manual).getPath, "--icons", "-a", "revversion=" + SBuildConfig.sbuildVersion)
    )

    if(result != 0) throw new RuntimeException("a2x failed. Return code: " + result)
  }

  Target("phony:manual-asciidoctor") dependsOn manual ~ SBuildConfig.scalaLibrary ~ asciidoctor ~ "scan:.sbuild/scala/SBuild.scala" ~ jruby exec { ctx: TargetContext =>
    import java.net.URL

    @scala.annotation.tailrec
    def printCp(cl: ClassLoader) {
      val cp = cl match {
        case null => return
        case cl: { def getURLs: Array[URL] } => "\n  " + cl.getURLs.map(_.toExternalForm).mkString("\n  ")
        case _ => "Not able to extract classloader URLs."
      }
      println("Classpath of ClassLoader: " + cl + ": " + cp)
      printCp(cl.getParent)
    }

    printCp(getClass.getClassLoader)

    new AsciiDoctor(
      backend = Some("html5"),
      targetDir = Some("target"),
      sourceFiles = manual.files
    ).execute
  }

  Target("phony:manual-asciidoctor-fork") dependsOn manual ~ SBuildConfig.scalaLibrary ~ asciidoctor ~ "scan:.sbuild/scala/SBuild.scala" ~ jruby exec { ctx: TargetContext =>

    val result = addons.support.ForkSupport.runJavaAndWait(
      classpath = Seq(Path(".sbuild/scala/SBuild.scala")) ++ (SBuildConfig.scalaLibrary ~ asciidoctor ~ jruby).files,
      arguments = Array(
        "AsciiDoctorRunner",
        "backend=html5",
        //        "baseDir=" + Path("src/manual").getPath,
        "targetDir=" + Path("target").getPath
      ) ++
        manual.files.map(_.getPath)
    )

    if(result != 0) throw new RuntimeException("AsciiDoctorRunner failed. Return code: " + result)
    ???
  }

}

import java.io.File
import scala.collection.JavaConverters._
import org.asciidoctor.Asciidoctor
import org.asciidoctor.Options
import org.asciidoctor.Attributes

object AsciiDoctorRunner {
  def main(args: Array[String]): Unit = {

    case class Args(
      backend: Option[String] = None,
      targetDir: Option[String] = None,
      sourceFiles: Seq[File] = Seq(),
      baseDir: Option[String] = None,
      gemPath: Option[String] = None)

    val parsed = args.foldLeft(Args()) { (args, param) =>
      param.split("=", 2) match {
        case Array("backend", backend) => args.copy(backend = Some(backend))
        case Array("targetDir", targetDir) => args.copy(targetDir = Some(targetDir))
        case Array("gemPath", gemPath) => args.copy(gemPath = Some(gemPath))
        case Array("baseDir", baseDir) => args.copy(baseDir = Some(baseDir))
        case Array(sourceFile) => args.copy(sourceFiles = new File(sourceFile) +: args.sourceFiles)
        case Array(unsupported, _) =>
          throw new RuntimeException("Unsupported parameter: " + unsupported +
            "\nUsage: main [backend=backend] [targetDir=targetDir] sourceFile+")
      }
    }

    new AsciiDoctor(
      backend = parsed.backend,
      targetDir = parsed.targetDir,
      sourceFiles = parsed.sourceFiles,
      gemPath = parsed.gemPath,
      baseDir = parsed.baseDir
    ).execute
  }
}

class AsciiDoctor(backend: Option[String],
                  targetDir: Option[String],
                  sourceFiles: Seq[File],
                  baseDir: Option[String] = None,
                  docType: Option[String] = None,
                  gemPath: Option[String] = None) {

  def execute: Unit = {

    val attributes = new Attributes()
    val options = new Options()

    options.setMkDirs(true)

    backend.map(options.setBackend(_))
    docType.map(options.setDocType(_))
    baseDir.map(d => options.setBaseDir(d))
    targetDir match {
      case Some(d) => options.setToDir(d)
      case None => options.setInPlace(true)
    }

    options.setAttributes(attributes)

    val asciidoctor = Asciidoctor.Factory.create()
    asciidoctor.renderFiles(sourceFiles.toList.asJava, options)

  }

}

