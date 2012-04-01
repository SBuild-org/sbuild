import de.tototec.sbuild._
import de.tototec.sbuild.TargetRefs._
import de.tototec.sbuild.ant._

import org.apache.tools.ant.taskdefs._

import scala.tools.ant._

@classpath("http://repo1.maven.org/maven2/org/apache/ant/ant/1.8.3/ant-1.8.3.jar",
  "http://repo1.maven.org/maven2/org/scala-lang/scala-library/2.9.1/scala-library-2.9.1.jar",
  "http://repo1.maven.org/maven2/org/scala-lang/scala-compiler/2.9.1/scala-compiler-2.9.1.jar")
class SBuild(implicit P: Project) {

  SchemeHandler("mvn", new MvnSchemeHandler(".sbuild/mvn",
    Seq("http://repo1.maven.org/maven1", "file:///home/lefou/.m2/repository-tototec")))

  val version = "0.0.1-SNAPSHOT"
  val jar = "target/de.tototec.sbuild-" + version + ".jar"

  Target("phony:all") dependsOn jar

  val clean = Target("phony:clean") exec {
    new Delete() {
      setProject(AntProject())
      setDir(Path("target"))
    }.execute
  }

  val compileCp = "mvn:org.scala-lang:scala-library:2.9.1" /
    "mvn:org.scala-lang:scala-compiler:2.9.1" /
    "mvn:de.tototec:de.tototec.cmdoption:0.1.0" /
    "mvn:org.apache.ant:ant:1.8.3"

  def scalac(sourceDir: String, targetDir: String, cp: org.apache.tools.ant.types.Path) {
    new Mkdir() {
      setProject(AntProject())
      setDir(Path(targetDir))
    }.execute

    // we want to use FastScala, but after it compiles successfully, it bails out with an internal error
    new Scalac() {
      setProject(AntProject())
      setSrcdir(AntPath(sourceDir))
      setDestdir(Path(targetDir))
      setTarget("jvm-1.5")
      setEncoding("UTF-8")
      setDeprecation("on")
      setUnchecked("on")
      // setLogging("verbose")
      setClasspath(cp)
      setForce(true)
    }.execute
  }

  Target("phony:compile") dependsOn compileCp exec {
    scalac(sourceDir = "src/main/scala", targetDir = "target/classes", cp = AntPath(compileCp))
  }

  val testCp = compileCp / "mvn:org.testng:testng:6.4" / jar

  Target("phony:testCompile") dependsOn testCp exec {
    scalac(sourceDir = "src/main/scala", targetDir = "target/test-classes", cp = AntPath(testCp))
  }

  Target(jar) dependsOn "compile" exec {
    new Jar() {
      setProject(AntProject())
      setDestFile(Path(jar))
      setBasedir(Path("target/classes"))
    }.execute
  }

}
