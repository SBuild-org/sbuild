import de.tototec.sbuild._
import de.tototec.sbuild.Target

class SBuild(implicit project: Project) {

  val cacheDir = ".cache/cmvn"

  Target.registerSchemeHandler("mvn", new CmvnSchemeHandler(cacheDir, "http://repo1.maven.org/maven2", "http://scala-tools.org/repo-releases"))

  import sys.process.Process
  import scala.tools.nsc.io.File
  import scala.tools.nsc.io.Directory
  
  val clean = Target("phony:clean") exec {
    println("Removing target")
    Process("rm -r target") !
  }

  val mvnClean = Target("phony:mvnclean") exec {
    println("Removing " + cacheDir)
    Process("rm -r " + cacheDir) !
  }

  val compileClasspath = Seq()
  val compile = Target("phony:compile") dependsOn compileClasspath exec {
    val dir = "src/main/scala"
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
