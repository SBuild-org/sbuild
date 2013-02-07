import de.tototec.sbuild._
import de.tototec.sbuild.TargetRefs._

object SBuildConfig {

  def sbuildVersion = "0.3.2.9001"
  def sbuildOsgiVersion = sbuildVersion

  def cmdOptionVersion = "0.2.1"
  def cmdOptionSource = s"http://cmdoption.tototec.de/cmdoption/attachments/download/13/de.tototec.cmdoption-${cmdOptionVersion}.jar"

  def scalaVersion = "2.10.0"
  def scalaBinVersion = "2.10"

  def compilerPath(implicit project: Project) =
    s"mvn:org.scala-lang:scala-library:${scalaVersion}" ~
    s"mvn:org.scala-lang:scala-compiler:${scalaVersion}" ~
    s"mvn:org.scala-lang:scala-reflect:${scalaVersion}"

}
