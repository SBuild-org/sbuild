import de.tototec.sbuild._
import de.tototec.sbuild.TargetRefs._
import de.tototec.sbuild.ant._
import de.tototec.sbuild.ant.tasks._

@version("0.1.0")
@classpath(
  "http://repo1.maven.org/maven2/org/apache/ant/ant/1.8.3/ant-1.8.3.jar",
  "http://repo1.maven.org/maven2/org/scala-lang/scala-compiler/2.9.2/scala-compiler-2.9.2.jar")
class SBuild(implicit project: Project) {

  SchemeHandler("http", new HttpSchemeHandler(Path(".sbuild/http")))
  SchemeHandler("mvn", new MvnSchemeHandler(Path(Prop("mvn.repo", ".sbuild/mvn"))))

  val version = Prop("SBUILD_VERSION", "svn")
  val addonsJar = "target/de.tototec.sbuild.addons.jar"

  val scalaVersion = "2.9.2"
  val compileCp =
    ("mvn:org.scala-lang:scala-library:" + scalaVersion) ~
      ("../de.tototec.sbuild/target/de.tototec.sbuild.jar") ~
      ("mvn: org.scalatest:scalatest_" + scalaVersion + ":1.6.1")

  SetProp("eclipse.classpath",
    compileCp.targetRefs.map(t => "<dep><![CDATA[" + (
      if (t.explicitProject.isDefined) (t.explicitProject + "::") else "") +
      t.name + "]]></dep>").mkString("<deps>", "", "</deps>"))

  Target("phony:all") dependsOn "clean" ~ addonsJar

  Target("phony:clean") exec {
    AntDelete(dir = Path("target"))
  }

  Target("phony:compile") dependsOn (compileCp) exec { ctx: TargetContext =>
    val input = "src/main/scala"
    val output = "target/classes"
    AntMkdir(dir = Path(output))
    IfNotUpToDate(srcDir = Path(input), stateDir = Path("target"), ctx = ctx) {
      scala_tools_ant.AntScalac(
        target = "jvm-1.5",
        encoding = "UTF-8",
        deprecation = "on",
        unchecked = "on",
        debugInfo = "vars",
        // this is necessary, because the scala ant tasks outsmarts itself 
        // when more than one scala class is defined in the same .scala file
        force = true,
        srcDir = AntPath(input),
        destDir = Path(output),
        classpath = AntPath(locations = ctx.fileDependencies))
    }
  }

  Target(addonsJar) dependsOn ("compile") exec { ctx: TargetContext =>
    val jarTask = new AntJar(destFile = ctx.targetFile.get, baseDir = Path("target/classes"))
    jarTask.addFileset(AntFileSet(dir = Path("."), includes = "LICENSE.txt"))
    jarTask.execute
  }

}
