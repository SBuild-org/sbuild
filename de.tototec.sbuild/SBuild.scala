import de.tototec.sbuild._
import de.tototec.sbuild.TargetRefs._
import de.tototec.sbuild.ant._

import org.apache.tools.ant.taskdefs._

import org.apache.tools.ant.taskdefs.optional.junit._

@classpath("http://repo1.maven.org/maven2/org/apache/ant/ant/1.8.3/ant-1.8.3.jar",
  "http://repo1.maven.org/maven2/org/apache/ant/ant-launcher/1.8.3/ant-launcher-1.8.3.jar",
  "http://repo1.maven.org/maven2/org/apache/ant/ant-junit/1.8.3/ant-junit-1.8.3.jar",
  "http://repo1.maven.org/maven2/junit/junit/4.10/junit-4.10.jar",
  "http://repo1.maven.org/maven2/org/scala-lang/scala-library/2.9.1/scala-library-2.9.1.jar",
  "http://repo1.maven.org/maven2/org/scala-lang/scala-compiler/2.9.1/scala-compiler-2.9.1.jar",
  "http://repo1.maven.org/maven2/org/scalatest/scalatest_2.9.1/1.6.1/scalatest_2.9.1-1.6.1.jar")
class SBuild(implicit P: Project) {

  SchemeHandler("http", new HttpSchemeHandler(".sbuild/http"))
  SchemeHandler("mvn", new MvnSchemeHandler(".sbuild/mvn"))

  val version = "0.0.1-SNAPSHOT"
  val jar = "target/de.tototec.sbuild-" + version + ".jar"

  val scalaVersion = "2.9.1"
  val compileCp =
    ("mvn:org.scala-lang:scala-library:" + scalaVersion) ~
      ("mvn:org.scala-lang:scala-compiler:" + scalaVersion) ~
      "http://cmdoption.tototec.de/cmdoption/attachments/download/3/de.tototec.cmdoption-0.1.0.jar" ~
      "mvn:org.apache.ant:ant:1.8.3"

  val testCp =
    compileCp ~
      jar ~
      ("mvn: org.scalatest:scalatest_" + scalaVersion + ":1.6.1") ~
      "mvn:junit:junit:4.10"

  Target("phony:all") dependsOn jar

  val clean = Target("phony:clean") exec {
    new Delete() { setProject(AntProject()); setDir(Path("target")) }.execute
  }

  def scalac(sourceDir: String, targetDir: String, cp: org.apache.tools.ant.types.Path) {
    new Mkdir() { setProject(AntProject()); setDir(Path(targetDir)) }.execute
    // we want to use FastScala, but after it compiles successfully, it bails out with an internal error
    new scala.tools.ant.Scalac() {
      setProject(AntProject())
      setSrcdir(AntPath(sourceDir))
      setDestdir(Path(targetDir))
      setTarget("jvm-1.5")
      setEncoding("UTF-8")
      setDeprecation("on")
      setUnchecked("on")
      // setLogging("verbose")
      setClasspath(cp)
      // this is necessary, because the scala ant tasks outsmarts itself 
      // when more than one scala class is defined in the same .scala file
      setForce(true)
    }.execute
  }

  Target("phony:compile") dependsOn compileCp exec { ctx: TargetContext =>
    val state = PersistentUpToDateChecker("compile", Path("src/main/scala"), Path("target"), ctx.prerequisites)
    ctx.targetWasUpToDate = !state.doWhenNotUpToDate {
      scalac(sourceDir = "src/main/scala", targetDir = "target/classes", cp = AntPath(compileCp))
    }
  }

  Target("phony:fake") exec {
    println("Fake")
    Path(jar).delete
  }

  Target("phony:testCompile") dependsOn testCp exec { ctx: TargetContext =>
    val state = PersistentUpToDateChecker("testCompile", Path("src/test/scala"), Path("target"), ctx.prerequisites)
    ctx.targetWasUpToDate = !state.doWhenNotUpToDate {
      scalac(sourceDir = "src/test/scala", targetDir = "target/test-classes", cp = AntPath(testCp))
    }
  }

  Target(jar) dependsOn "compile" exec { ctx: TargetContext =>
    new Jar() {
      setProject(AntProject())
      setDestFile(ctx.targetFile.get)
      setBasedir(Path("target/classes"))
    }.execute
  }

  Target("target/test.jar") dependsOn "testCompile" exec { ctx: TargetContext =>
    new Jar() {
      setProject(AntProject())
      setDestFile(ctx.targetFile.get)
      setBasedir(Path("target/test-classes"))
    }.execute
  }

  Target("phony:scaladoc") dependsOn compileCp exec { ctx: TargetContext =>
    new Mkdir() { setProject(AntProject()); setDir(Path("target/scaladoc")) }.execute
    new scala.tools.ant.Scaladoc() {
      setProject(AntProject())
      // setWindowtitle("SBuild API Documentation")
      setDeprecation("on")
      setUnchecked("on")
      setClasspath(AntPath(ctx.prerequisites))
      setSrcdir(AntPath("src/main/scala"))
      setDestdir(Path("target/scaladoc"))
      // setLogging("verbose")
    }.execute
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
