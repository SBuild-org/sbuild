import de.tototec.sbuild._
import de.tototec.sbuild.TargetRefs._

object SBuildConfig {

  def sbuildVersion = "0.4.0.9001"
  def sbuildOsgiVersion = sbuildVersion

  def cmdOptionVersion = "0.3.0"
  def cmdOption = s"mvn:de.tototec:de.tototec.cmdoption:${cmdOptionVersion}"

  def jansiVersion = "1.10"
  val jansi = s"mvn:org.fusesource.jansi:jansi:${jansiVersion}"

  def scalaVersion = "2.10.1"
  def scalaBinVersion = "2.10"

  def compilerPath(implicit project: Project) =
    s"mvn:org.scala-lang:scala-library:${scalaVersion}" ~
    s"mvn:org.scala-lang:scala-compiler:${scalaVersion}" ~
    s"mvn:org.scala-lang:scala-reflect:${scalaVersion}"

}
