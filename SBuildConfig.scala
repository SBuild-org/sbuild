import de.tototec.sbuild._
import de.tototec.sbuild.TargetRefs._

object SBuildConfig {

  def sbuildVersion = "0.5.0.9001"
  def sbuildOsgiVersion = sbuildVersion

  private def cmdOptionVersion = "0.3.1"
  def cmdOption = s"mvn:de.tototec:de.tototec.cmdoption:${cmdOptionVersion}"
  // def cmdOption = s"/home/lefou/work/tototec/cmdoption-trunk/de.tototec.cmdoption/target/de.tototec.cmdoption-${cmdOptionVersion}.jar"

  private def jansiVersion = "1.11"
  val jansi = s"mvn:org.fusesource.jansi:jansi:${jansiVersion}"

  def scalaVersion = "2.10.2"
  def scalaBinVersion = "2.10"

  def compilerPath(implicit project: Project) =
    s"mvn:org.scala-lang:scala-library:${scalaVersion}" ~
    s"mvn:org.scala-lang:scala-compiler:${scalaVersion}" ~
    s"mvn:org.scala-lang:scala-reflect:${scalaVersion}"

}
