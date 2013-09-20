import de.tototec.sbuild._
import de.tototec.sbuild.ant._
import de.tototec.sbuild.ant.tasks._
import de.tototec.sbuild.TargetRefs._

@version("0.4.0")
@include("../SBuildConfig.scala")
@classpath("mvn:org.apache.ant:ant:1.8.4",
  "mvn:org.unix4j:unix4j-base:0.3",
  "mvn:org.unix4j:unix4j-command:0.3"
)
class SBuild(implicit _project: Project) {

  val jar = s"target/de.tototec.sbuild.ant-${SBuildConfig.sbuildVersion}.jar"

  val compileCp =
    s"../de.tototec.sbuild/target/de.tototec.sbuild-${SBuildConfig.sbuildVersion}.jar" ~
      "mvn:org.apache.ant:ant:1.8.4" ~
      SBuildConfig.scalaLibrary ~
      SBuildConfig.scalaCompiler ~
      SBuildConfig.scalaReflect ~
      "mvn:org.liquibase:liquibase-core:2.0.3" ~
      "mvn:biz.aQute:bnd:1.50.0"

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
      fileSet = AntFileSet(dir = Path("."), includes = "LICENSE.txt"),
      manifestEntries = Map("SBuild-ComponentName" -> "de.tototec.sbuild.ant")
    )
  }

  Target("phony:scaladoc").cacheable dependsOn SBuildConfig.compilerPath ~ compileCp ~~
    "scan:src/main/scala" ~ "src/main/scaladoc/root.tracwiki" exec {
      import java.io.File

      def substVars(source: File, target: File) = {
        target.getParentFile.mkdirs
        org.unix4j.Unix4j.cat(source).sed(s"s/\\$$#\\{SBUILD_VERSION\\}/${SBuildConfig.sbuildVersion}/g").toFile(target)
      }

      def substVarsInDir(sourceDir: File, targetDir: File, files: Seq[File]) = {
        val prefixL = sourceDir.getPath.length
        files.foreach { file =>
          val relFile = file.getPath.substring(prefixL)
          val targetFile = Path(targetDir, relFile)
          substVars(file, targetFile)
        }
      }

      // parse root page
      val targetRoot = Path("target/generated-scaladoc/root.tracwiki")
      substVars(Path("src/main/scaladoc/root.tracwiki"), targetRoot)

      substVarsInDir(Path("src/main/scala"), Path("target/generated-scaladoc"), "scan:src/main/scala".files)

      addons.scala.Scaladoc(
        scaladocClasspath = SBuildConfig.compilerPath.files,
        classpath = compileCp.files,
        srcDir = Path("target/generated-scaladoc"),
        destDir = Path("target/scaladoc"),
        deprecation = true, unchecked = true, implicits = true,
        docVersion = SBuildConfig.sbuildVersion,
        docTitle = "SBuild Ant Support and Wrappers Reference",
        additionalScaladocArgs = Seq("-doc-root-content", targetRoot.getPath)
      )
    }

}
