import de.tototec.sbuild._
import de.tototec.sbuild.TargetRefs._

object SBuildConfig {

  def sbuildVersion = "0.3.0"
  def sbuildOsgiVersion = sbuildVersion

  def cmdOptionVersion = "0.2.0"
  def cmdOptionSource = "http://cmdoption.tototec.de/cmdoption/attachments/download/6/de.tototec.cmdoption-0.2.0.jar"

  def scalaVersion = "2.10.0"
  def scalaBinVersion = "2.10"

  def compilerPath(implicit project: Project) =
    ("mvn:org.scala-lang:scala-library:" + scalaVersion) ~
    ("mvn:org.scala-lang:scala-compiler:" + scalaVersion) ~
    ("mvn:org.scala-lang:scala-reflect:" + scalaVersion)

}
