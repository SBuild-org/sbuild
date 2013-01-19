import de.tototec.sbuild._
import de.tototec.sbuild.TargetRefs._
import de.tototec.sbuild.ant._
import de.tototec.sbuild.ant.tasks._

import de.tototec.sbuild.natures.experimental._

@version("0.3.1.9000") 
// Lets just use all the natures of this project directly :-)
@include(
  "../SBuildConfig.scala",
  "Natures-Snapshot-201301191524.scala"
)
@classpath(
  "mvn:org.apache.ant:ant:1.8.4"
)
class SBuild(implicit project: Project) {

  val compileCp =
    s"mvn:org.scala-lang:scala-library:${SBuildConfig.scalaVersion}" ~
      s"../de.tototec.sbuild/target/de.tototec.sbuild-${SBuildConfig.sbuildVersion}.jar" ~
      s"../de.tototec.sbuild.ant/target/de.tototec.sbuild.ant-${SBuildConfig.sbuildVersion}.jar" ~
      s"../de.tototec.sbuild.addons/target/de.tototec.sbuild.addons-${SBuildConfig.sbuildVersion}.jar" ~
      "mvn:org.apache.ant:ant:1.8.4"

  ExportDependencies("eclipse.classpath", compileCp)

  val tAll = Target("phony:all") help "Default target: Build all"

  new CleanNature with CompileScalaNature with JarNature with ScalaSourcesNature {

    override def artifact_name = "de.tototec.sbuild.natures"
    override def artifact_version = SBuildConfig.sbuildVersion
    override def compileScala_compileClasspath = compileCp
    override def jar_dependsOn = compileScala_targetName

    tAll dependsOn jar_output

  }.createTargets

}
