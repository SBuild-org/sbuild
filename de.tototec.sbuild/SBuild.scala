import de.tototec.sbuild._
import de.tototec.sbuild.ant._
import de.tototec.sbuild.ant.tasks._
//import de.tototec.sbuild.ant.tasks.scala_tools_ant._
import de.tototec.sbuild.TargetRefs._

import org.apache.tools.ant.taskdefs.optional.junit._

@classpath(
  "http://repo1.maven.org/maven2/org/apache/ant/ant/1.8.3/ant-1.8.3.jar",
  "http://repo1.maven.org/maven2/org/apache/ant/ant-launcher/1.8.3/ant-launcher-1.8.3.jar",
  "http://repo1.maven.org/maven2/org/apache/ant/ant-junit/1.8.3/ant-junit-1.8.3.jar",
  "http://repo1.maven.org/maven2/junit/junit/4.10/junit-4.10.jar",
  "http://repo1.maven.org/maven2/org/scala-lang/scala-compiler/2.9.2/scala-compiler-2.9.2.jar",
  "http://repo1.maven.org/maven2/org/scalatest/scalatest_2.9.2/1.6.1/scalatest_2.9.2-1.6.1.jar"
)
class SBuild(implicit project: Project) {

  SchemeHandler("http", new HttpSchemeHandler(Path(".sbuild/http")))
  SchemeHandler("mvn", new MvnSchemeHandler(Path(Prop("mvn.repo", ".sbuild/mvn"))))
  
  val version = Prop("SBUILD_VERSION")
  val jar = "target/de.tototec.sbuild-" + version + ".jar"

  val scalaVersion = "2.9.2"
  val compileCp =
    ("mvn:org.scala-lang:scala-library:" + scalaVersion) ~
      "http://cmdoption.tototec.de/cmdoption/attachments/download/3/de.tototec.cmdoption-0.1.0.jar" ~
      "mvn:org.apache.ant:ant:1.8.3"

  val testCp =
    compileCp ~
      jar ~
      ("mvn: org.scalatest:scalatest_" + scalaVersion + ":1.6.1") ~
      "mvn:junit:junit:4.10"

  Target("phony:all") dependsOn jar

  val clean = Target("phony:clean") exec {
    AntDelete(dir = Path("target"))
  }

  def antScalac = new scala_tools_ant.AntScalac(
    target = "jvm-1.5",
    encoding = "UTF-8",
    deprecation = "on",
    unchecked = "on",
    debugInfo = "vars",
    // this is necessary, because the scala ant tasks outsmarts itself 
    // when more than one scala class is defined in the same .scala file
    force = true)

  Target("phony:compile") dependsOn compileCp exec { ctx: TargetContext =>
    val input = "src/main/scala"
    val output = "target/classes"
    AntMkdir(dir = Path(output))
    IfNotUpToDate(srcDir = Path(input), stateDir = Path("target"), ctx = ctx) {
      val scalac = antScalac
      scalac.setSrcDir(AntPath(input))
      scalac.setDestDir(Path(output))
      scalac.setClasspath(AntPath(compileCp))
      scalac.execute
    }
  }

  Target("phony:copyResources") exec {
    val resources = Path("src/main/resources")
    if(resources.exists) {
      new AntCopy(toDir = Path("target/classes")) {
        add(AntPath(resources))
      }.execute
    }
  }

  Target("phony:testCompile") dependsOn testCp exec { ctx: TargetContext =>
    val input = "src/test/scala"
    val output = "target/test-classes"
    AntMkdir(dir = output)
    IfNotUpToDate(Path(input), Path("target"), ctx) {
      val scalac = antScalac
      scalac.setSrcDir(AntPath(input))
      scalac.setDestDir(Path(output))
      scalac.setClasspath(AntPath(testCp))
      scalac.execute
    }
  }

  Target(jar) dependsOn ("compile" ~ "copyResources") exec { ctx: TargetContext =>
    AntJar(destFile = ctx.targetFile.get, baseDir = Path("target/classes"))
  }

  Target("target/test.jar") dependsOn "testCompile" exec { ctx: TargetContext =>
    AntJar(destFile = ctx.targetFile.get, baseDir = Path("target/test-classes"))
  }

  Target("target/sources.jar") exec { ctx: TargetContext =>
    AntCopy(file = Path("src/main/scala"), toDir = Path("target/sources/src/main/scala"))
    AntCopy(file = Path("src/main/resources"), toDir = Path("target/sources/src/main/resources"))
    AntJar(destFile = ctx.targetFile.get, baseDir = Path("target/sources"))
  }

  Target("phony:scaladoc") dependsOn compileCp exec { ctx: TargetContext =>
    AntMkdir(dir = Path("target/scaladoc"))
    scala_tools_ant.AntScaladoc(
      deprecation ="on",
      unchecked = "on",
      classpath = AntPath(ctx.prerequisites),
      srcDir = AntPath("src/main/scala"),
      destDir = Path("target/scaladoc")
    )
  }

  Target("phony:test") dependsOn "target/test.jar" ~ testCp exec { ctx: TargetContext =>
    new JUnitTask() {
      setProject(AntProject())
      setFork(true)
      //      ctx.prerequisites.foreach { d =>
      //        d.targetFile match {
      //          case Some(f) => addClasspathEntry(f.path)
      //        }
      //      }
      addClasspathEntry("target/test.jar")
      //       setClasspath(AntPath(testCp))
      //       setClasspath(AntPath("target/test.jar"))
      addTest(new JUnitTest() {
        setName("de.tototec.sbuild.CmvnSchemeHandlerTest")
      })
    }.execute

  }

  Target("phony:testScalaTest") dependsOn "target/test.jar" ~ testCp exec {
    new org.scalatest.tools.ScalaTestAntTask() {
      setProject(AntProject())
      setFork(true)
      setRunpath(AntPath(testCp))
    }.execute
  }

}
