import de.tototec.sbuild._
import de.tototec.sbuild.TargetRefs._
import de.tototec.sbuild.ant._
import de.tototec.sbuild.ant.tasks._

@version("0.4.0")
@include("../SBuildConfig.scala",
  "src/main/scala/de/tototec/sbuild/addons/java/Javadoc.scala"
)
@classpath("mvn:org.apache.ant:ant:1.8.4")
class SBuild(implicit _project: Project) {

  val addonsJar = s"target/de.tototec.sbuild.addons-${SBuildConfig.sbuildVersion}.jar"

  val compileCp =
    SBuildConfig.scalaLibrary ~
    s"../de.tototec.sbuild/target/de.tototec.sbuild-${SBuildConfig.sbuildVersion}.jar"
  //      ("mvn: org.scalatest:scalatest_" + scalaVersion + ":1.6.1") ~
  //      ("http://cloud.github.com/downloads/KentBeck/junit/junit-4.10.jar") ~
  //      ("http://cloud.github.com/downloads/KentBeck/junit/junit-4.10-src.jar")
  //      "mvn:biz.aQute:bndlib:1.50.0"

  ExportDependencies("eclipse.classpath", compileCp)

  Target("phony:all") dependsOn addonsJar

  Target("phony:clean").evictCache exec {
    AntDelete(dir = Path("target"))
  }

  Target("phony:compile").cacheable dependsOn SBuildConfig.compilerPath ~ compileCp ~ "scan:src/main/scala" exec {
    val input = "src/main/scala"
    val output = "target/classes"

    addons.scala.Scalac(
      deprecation = true, unchecked = true, debugInfo = "vars", target = "jvm-1.6",
      compilerClasspath = SBuildConfig.compilerPath.files,
      classpath = compileCp.files,
      sources = "scan:src/main/scala".files,
      destDir = Path(output)
    )
  }

  Target(addonsJar) dependsOn ("compile") exec { ctx: TargetContext =>
    AntJar(
      destFile = ctx.targetFile.get, 
      baseDir = Path("target/classes"),
      fileSet = AntFileSet(dir = Path("."), includes = "LICENSE.txt")
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
      docTitle = s"SBuild Addons Reference"
    )
  }

  val genJavaDoc = s"mvn:com.typesafe.genjavadoc:genjavadoc-plugin_${SBuildConfig.scalaVersion}:0.4"

  Target("phony:genjavadoc").cacheable dependsOn
    SBuildConfig.compilerPath ~ genJavaDoc ~ compileCp ~ "scan:src/main/scala" exec {
    addons.scala.Scalac(
      compilerClasspath = SBuildConfig.compilerPath.files,
      classpath = compileCp.files,
      sources = "scan:src/main/scala".files,
      destDir = Path("target/genjavadoc-classes"),
      unchecked = true, deprecation = true, debugInfo = "vars",
      additionalScalacArgs = Seq(
        s"""-Xplugin:${genJavaDoc.files.head.getPath}""",
        s"""-P:genjavadoc:out=${Path("target/genjavadoc")}"""
      )
    )
  }

  Target("phony:javadoc").cacheable dependsOn "genjavadoc" ~
    SBuildConfig.compilerPath ~ compileCp ~ "scan:target/genjavadoc" exec {
    addons.java.Javadoc(
      classpath = compileCp.files,
      sources = "scan:target/genjavadoc".files,
      destDir = Path("target/javadoc"),
      fork = true
    )
  }

}
