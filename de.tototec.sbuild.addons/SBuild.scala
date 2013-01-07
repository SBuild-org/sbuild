import de.tototec.sbuild._
import de.tototec.sbuild.TargetRefs._
import de.tototec.sbuild.ant._
import de.tototec.sbuild.ant.tasks._

@version("0.2.0")
@include(
  "../SBuildConfig.scala"
)
@classpath(
  "http://repo1.maven.org/maven2/org/apache/ant/ant/1.8.3/ant-1.8.3.jar"
)
class SBuild(implicit _project: Project) {

  SchemeHandler("http", new HttpSchemeHandler(Path(".sbuild/http")))
  SchemeHandler("mvn", new MvnSchemeHandler())

  val addonsJar = s"target/de.tototec.sbuild.addons-${SBuildConfig.sbuildVersion}.jar"

  val compileCp =
    s"mvn:org.scala-lang:scala-library:${SBuildConfig.scalaVersion}" ~
      s"../de.tototec.sbuild/target/de.tototec.sbuild-${SBuildConfig.sbuildVersion}.jar" ~
      //      ("mvn: org.scalatest:scalatest_" + scalaVersion + ":1.6.1") ~
      //      ("http://cloud.github.com/downloads/KentBeck/junit/junit-4.10.jar") ~
      //      ("http://cloud.github.com/downloads/KentBeck/junit/junit-4.10-src.jar")
      "mvn:biz.aQute:bndlib:1.50.0"

  ExportDependencies("eclipse.classpath", compileCp)

  Target("phony:all") dependsOn "clean" ~ addonsJar

  Target("phony:clean") exec {
    AntDelete(dir = Path("target"))
  }

  Target("phony:compile") dependsOn SBuildConfig.compilerPath ~ compileCp exec { ctx: TargetContext =>
    val input = "src/main/scala"
    val output = "target/classes"
    AntMkdir(dir = Path(output))
    IfNotUpToDate(Path(input), Path("target"), ctx) {

      val compilerFilter = """.*scala-((library)|(compiler)|(reflect)).*""".r

      new addons.scala.Scalac(
        deprecation = true, unchecked = true, debugInfo = "vars", target = "jvm-1.5",
        fork = true,
        compilerClasspath = ctx.fileDependencies.filter(f => compilerFilter.pattern.matcher(f.getName).matches),
        srcDir = Path(input),
        destDir = Path(output),
        classpath = ctx.fileDependencies
      ).execute
    }
  }
  Target(addonsJar) dependsOn ("compile") exec { ctx: TargetContext =>
    val jarTask = new AntJar(destFile = ctx.targetFile.get, baseDir = Path("target/classes"))
    jarTask.addFileset(AntFileSet(dir = Path("."), includes = "LICENSE.txt"))
    jarTask.execute
  }

}
