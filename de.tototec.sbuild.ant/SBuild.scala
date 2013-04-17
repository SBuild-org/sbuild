import de.tototec.sbuild._
import de.tototec.sbuild.ant._
import de.tototec.sbuild.ant.tasks._
import de.tototec.sbuild.TargetRefs._

@version("0.4.0")
@include("../SBuildConfig.scala")
@classpath("mvn:org.apache.ant:ant:1.8.4")
class SBuild(implicit _project: Project) {

  val jar = s"target/de.tototec.sbuild.ant-${SBuildConfig.sbuildVersion}.jar"

  // Current version of bnd (with ant tasks) is not in Maven repo 
  val bnd_1_50_0 = "http://dl.dropbox.com/u/2590603/bnd/biz.aQute.bnd.jar"

  val compileCp =
    s"../de.tototec.sbuild/target/de.tototec.sbuild-${SBuildConfig.sbuildVersion}.jar" ~
      s"mvn:org.scala-lang:scala-library:${SBuildConfig.scalaVersion}" ~
      s"mvn:org.scala-lang:scala-compiler:${SBuildConfig.scalaVersion}" ~
      s"mvn:org.scala-lang:scala-reflect:${SBuildConfig.scalaVersion}" ~
      "mvn:org.apache.ant:ant:1.8.4" ~
      "mvn:org.liquibase:liquibase-core:2.0.3" ~
      bnd_1_50_0

  ExportDependencies("eclipse.classpath", compileCp)

  Target("phony:all") dependsOn jar

  Target("phony:clean").evictCache exec {
    AntDelete(dir = Path("target"))
  }

  Target("phony:compile").cacheable dependsOn SBuildConfig.compilerPath ~ compileCp ~ "scan:src/main/scala" exec {
    val input = "src/main/scala"
    val output = "target/classes"

    addons.scala.Scalac(
      deprecation = true, unchecked = true, debugInfo = "vars",
      compilerClasspath = SBuildConfig.compilerPath.files,
      classpath = compileCp.files,
      sources = "scan:src/main/scala".files,
      destDir = Path(output)
    )
  }

  Target(jar) dependsOn "compile" ~ "LICENSE.txt" exec { ctx: TargetContext =>
    AntJar(destFile = ctx.targetFile.get, baseDir = Path("target/classes"),
      fileSet = AntFileSet(dir = Path("."), includes="LICENSE.txt"),
      manifestEntries = Map("SBuild-ComponentName" -> "de.tototec.sbuild.ant")
    )
  }

  Target("phony:scaladoc").cacheable dependsOn SBuildConfig.compilerPath ~ compileCp ~ "scan:src/main/scala" exec {
    addons.scala.Scaladoc(
      scaladocClasspath = SBuildConfig.compilerPath.files,
      classpath = compileCp.files,
      sources = "scan:src/main/scala".files,
      destDir = Path("target/scaladoc"),
      deprecation = true, unchecked = true, implicits = true,
      docVersion = SBuildConfig.sbuildVersion,
      docTitle = "SBuild Ant Support and Wrappers Reference"
    )
  }

}
