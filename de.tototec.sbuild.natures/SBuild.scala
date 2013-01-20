import de.tototec.sbuild._
import de.tototec.sbuild.TargetRefs._
import de.tototec.sbuild.ant._
import de.tototec.sbuild.ant.tasks._

import de.tototec.sbuild.natures.experimental._

@version("0.3.1") 
// Lets just use all the natures of this project directly :-)
@include(
  "../SBuildConfig.scala",
  "Natures-Snapshot-201301191524.scala"
)
@classpath(
  "http://repo1.maven.org/maven2/org/apache/ant/ant/1.8.4/ant-1.8.4.jar"
)
class SBuild(implicit project: Project) {

  SchemeHandler("mvn", new MvnSchemeHandler())
  SchemeHandler("http", new HttpSchemeHandler())
  SchemeHandler("zip", new ZipSchemeHandler())

  val compileCp =
    s"mvn:org.scala-lang:scala-library:${SBuildConfig.scalaVersion}" ~
      s"../de.tototec.sbuild/target/de.tototec.sbuild-${SBuildConfig.sbuildVersion}.jar" ~
      s"../de.tototec.sbuild.ant/target/de.tototec.sbuild.ant-${SBuildConfig.sbuildVersion}.jar" ~
      s"../de.tototec.sbuild.addons/target/de.tototec.sbuild.addons-${SBuildConfig.sbuildVersion}.jar" ~
      "mvn:org.apache.ant:ant:1.8.4"

  ExportDependencies("eclipse.classpath", compileCp)

  val tAll = Target("phony:all") help "Default target: Build all"

  new CleanNature with CompileScalaNature with JarNature with ScalaSourcesNature {

    override def artifact_name = "de.tototec.sbuild.natures.experimental"
    override def artifact_version = SBuildConfig.sbuildVersion
    override def compileScala_compileClasspath = compileCp
    override def jar_dependsOn = compileScala_targetName

    tAll dependsOn jar_output

  }.createTargets

}
