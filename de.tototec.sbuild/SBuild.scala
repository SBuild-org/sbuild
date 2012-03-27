import de.tototec.sbuild._
import org.apache.ant.taskdefs._

@classpath("/home/lefou/.m2/repository-tototec/org/testng/testng/6.4/testng-6.4.jar")
class SBuild(implicit P: Project) {

  val cacheDir = Path(".m2/repository")
  SchemeHandler("mvn", new MvnSchemeHandler(cacheDir.getPath, Seq("http://repo1.maven.org/maven2")))

  val clean = Target("phony:clean") exec {
    new Delete() {
      setDir(Path("target"))
    }.execute
  }

  Target("phony:mvnclean") exec {
    new Delete() {
      setDir(cacheDir)
    }.execute
  }

  val compileClasspath = Seq()
  val compile = Target("phony:compile") dependsOn compileClasspath exec {

    val dir = Path("src/main/scala")
    //    val classpath = compileClasspath.map(_.targetFile.get.getAbsolutePath).mkString(":")
    val sources = Util.recursiveListFilesAbsolute(dir).mkString(" ")
    Directory("target/classes").createDirectory()
    val cmdline = "fsc -encoding UTF-8 -deprecation -explaintypes -d target/classes " + sources
    println("Executing: " + cmdline)
    Process(cmdline) !!
  }

  val testng = Target("mvn:org.testng:testng:6.0.1")
  val testCompileClasspath = compileClasspath ++ testng
  val testCompile = Target("phony:test-compile") dependsOn compile ++ testCompileClasspath exec {
    val classpath = (Seq("target/classes") ++ (testCompileClasspath.map(_.targetFile.get.getAbsolutePath))).mkString(":")
    val sources = Util.recursiveListFilesAbsolute("src/test/scala").mkString(" ")
    Directory("target/test-classes").createDirectory()
    val cmdline = "fsc -encoding UTF-8 -deprecation -explaintypes -d target/test-classes -classpath " + classpath + " " + sources
    println("Executing: " + cmdline)
    Process(cmdline) !!
  }

  val testRunClasspath = testCompileClasspath
  val test = Target("phony:test") dependsOn testCompile ++ testRunClasspath exec {
  }

  val jar = Target("target/package.jar") dependsOn compile
  Target("phony:jar") dependsOn jar

}
