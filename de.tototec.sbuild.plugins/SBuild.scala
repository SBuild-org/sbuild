import de.tototec.sbuild._
import de.tototec.sbuild.TargetRefs._
import de.tototec.sbuild.ant._
import de.tototec.sbuild.ant.tasks._
import de.tototec.sbuild.addons.scala.Scalac

@version("0.4.0")
@classpath("mvn:org.apache.ant:ant:1.9.0")
@include("../SBuildConfig.scala")
class SBuild(implicit _project: Project) {

  val scalaVersion = SBuildConfig.scalaVersion
  val sbuildVersion = SBuildConfig.sbuildVersion

  val jar = s"target/de.tototec.sbuild.plugins-${sbuildVersion}.jar"

  val compilerCp = SBuildConfig.compilerPath

  val compileCp =
    SBuildConfig.scalaLibrary ~
      s"../de.tototec.sbuild/target/de.tototec.sbuild-${sbuildVersion}.jar" ~
      s"../de.tototec.sbuild.addons/target/de.tototec.sbuild.addons-${sbuildVersion}.jar" ~
      s"../de.tototec.sbuild.ant/target/de.tototec.sbuild.ant-${sbuildVersion}.jar" ~
      "mvn:org.apache.ant:ant:1.8.4"

  ExportDependencies("eclipse.classpath", compileCp)

  Target("phony:clean").evictCache exec {
    AntDelete(dir = Path("target"))
  }

  Target("phony:all") dependsOn "jar"
  Target("phony:jar") dependsOn jar

  Target("phony:compile").cacheable dependsOn compileCp ~ compilerCp ~ "scan:src/main/scala" exec {
    Scalac(
      compilerClasspath = compilerCp.files,
      classpath = compileCp.files,
      sources = "scan:src/main/scala".files,
      destDir = Path("target/classes"),
      debugInfo = "vars")
  }

  Target(jar) dependsOn "compile" exec { ctx: TargetContext =>
    AntJar(destFile = ctx.targetFile.get, baseDir = Path("target/classes"))
  }

}
