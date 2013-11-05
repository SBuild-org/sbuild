import de.tototec.sbuild._
import de.tototec.sbuild.TargetRefs._
import de.tototec.sbuild.ant.tasks._

object SBuildConfig {

  def sbuildVersion = "0.6.0.9003"
  def sbuildOsgiVersion = sbuildVersion

  private val cmdOptionVersion = "0.3.1"
  val cmdOption = s"mvn:de.tototec:de.tototec.cmdoption:${cmdOptionVersion}"
  // def cmdOption = s"/home/lefou/work/tototec/cmdoption-trunk/de.tototec.cmdoption/target/de.tototec.cmdoption-${cmdOptionVersion}.jar"

  private def jansiVersion = "1.11"
  val jansi = s"mvn:org.fusesource.jansi:jansi:${jansiVersion}"

  def scalaVersion = "2.10.3"
  def scalaBinVersion = "2.10"

  def scalaLibrary = s"mvn:org.scala-lang:scala-library:${scalaVersion}"
  def scalaCompiler = s"mvn:org.scala-lang:scala-compiler:${scalaVersion}"
  def scalaReflect = s"mvn:org.scala-lang:scala-reflect:${scalaVersion}"

  def compilerPath(implicit project: Project) =
    scalaLibrary ~ scalaCompiler ~ scalaReflect

  val slf4jApi = "mvn:org.slf4j:slf4j-api:1.7.5"
  val jclOverSlf4j = "mvn:org.slf4j:jcl-over-slf4j:1.7.5"
  val log4jOverSlf4j = "mvn:org.slf4j:log4j-over-slf4j:1.7.5"
  val logbackCore = "mvn:ch.qos.logback:logback-core:1.0.13"
  val logbackClassic = "mvn:ch.qos.logback:logback-classic:1.0.13"

}

class I18n()(implicit _project: Project) {

  def applyAll {

  val msgSources = "src/main/scala"
  val msgCatalog = "target/po/messages.pot"

  Target("phony:xgettext") dependsOn msgCatalog

  Target(msgCatalog) dependsOn s"scan:${msgSources}" exec { ctx: TargetContext =>
    ctx.targetFile.get.getParentFile.mkdirs

    import java.io.File
    val srcDirUri = Path(msgSources).toURI

    AntExec(
      failOnError = true,
      executable = "xgettext",
      args = Array[String](
        "-ktr", "-kmarktr",
        "--language", "Java",
        "--directory", new File(srcDirUri).getPath,
        "--output-dir", ctx.targetFile.get.getParent,
        "--output", ctx.targetFile.get.getName) ++
        s"scan:${msgSources}".files.map(file => srcDirUri.relativize(file.toURI).getPath)
    )
  }
  }

}
