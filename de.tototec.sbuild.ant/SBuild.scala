import de.tototec.sbuild._
import de.tototec.sbuild.ant._
import de.tototec.sbuild.ant.tasks._
import de.tototec.sbuild.TargetRefs._

@classpath(
  "http://repo1.maven.org/maven2/org/apache/ant/ant/1.8.3/ant-1.8.3.jar",
  "http://repo1.maven.org/maven2/org/scala-lang/scala-library/2.9.2/scala-library-2.9.2.jar",
  "http://repo1.maven.org/maven2/org/scala-lang/scala-compiler/2.9.2/scala-compiler-2.9.2.jar"
)
class SBuild(implicit project: Project) {

  SchemeHandler("http", new HttpSchemeHandler(Path(".sbuild/http")))
  SchemeHandler("mvn", new MvnSchemeHandler(Path(".sbuild/mvn")))

  val version = "0.0.1-SNAPSHOT"
  val jar = "target/de.tototec.sbuild.ant-" + version + ".jar"

  val scalaVersion = "2.9.2"
  val compileCp =
    ("../de.tototec.sbuild/target/de.tototec.sbuild-" + version + ".jar") ~
    ("mvn:org.scala-lang:scala-library:" + scalaVersion) ~
    ("mvn:org.scala-lang:scala-compiler:" + scalaVersion) ~
    "mvn:org.apache.ant:ant:1.8.3" ~
    "mvn:org.liquibase:liquibase-core:2.0.3"

  Target("phony:all") dependsOn jar

  val clean = Target("phony:clean") exec {
    AntDelete(dir = Path("target"))
  }

  def antScalac = new scala_tools_ant.AntScalac(
    target = "jvm-1.5",
    encoding = "UTF-8",
    deprecation = "on",
    unchecked = "on",
    // this is necessary, because the scala ant tasks outsmarts itself 
    // when more than one scala class is defined in the same .scala file
    force = true)

  Target("phony:compile") dependsOn compileCp exec { ctx: TargetContext =>
    val input = "src/main/scala"
    val output = "target/classes"
    AntMkdir(dir = Path(output))
    IfNotUpToDate(Path(input), Path("target"), ctx) {
      val scalac = antScalac
      scalac.setSrcDir(AntPath(input))
      scalac.setDestDir(Path(output))
      scalac.setClasspath(AntPath(compileCp))
      scalac.execute
    }
  }

  Target(jar) dependsOn "compile" exec { ctx: TargetContext =>
    AntJar(destFile = ctx.targetFile.get, baseDir = Path("target/classes"))
  }

  Target("phony:scaladoc") dependsOn compileCp exec { ctx: TargetContext =>
    AntMkdir(dir = Path("target/scaladoc"))
    IfNotUpToDate(Path("src/main/scala"), Path("target"), ctx) {
      scala_tools_ant.AntScaladoc(
        deprecation ="on",
        unchecked = "on",
        classpath = AntPath(ctx.prerequisites),
        srcDir = AntPath("src/main/scala"),
        destDir = Path("target/scaladoc")
      )
    }
  }

}
