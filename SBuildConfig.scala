import de.tototec.sbuild._
import de.tototec.sbuild.TargetRefs._

object SBuildConfig {

  def sbuildVersion = "0.6.0.9001"
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
  def scalaActors = s"mvn:org.scala-lang:scala-actors:${scalaVersion}"

  def compilerPath(implicit project: Project) =
    scalaLibrary ~ scalaCompiler ~ scalaReflect

  val slf4jApi = "mvn:org.slf4j:slf4j-api:1.7.5"
  val logbackCore = "mvn:ch.qos.logback:logback-core:1.0.9"
  val logbackClassic = "mvn:ch.qos.logback:logback-classic:1.0.9"

}
