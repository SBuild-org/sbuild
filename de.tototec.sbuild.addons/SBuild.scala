import de.tototec.sbuild._
import de.tototec.sbuild.TargetRefs._
import de.tototec.sbuild.ant._
import de.tototec.sbuild.ant.tasks._

@version("0.3.2")
@include(
  "../SBuildConfig.scala",
  "src/main/scala/de/tototec/sbuild/addons/support/ForkSupport.scala",
  "src/main/scala/de/tototec/sbuild/addons/scala/Scaladoc.scala"
)
@classpath("mvn:org.apache.ant:ant:1.8.4")
class SBuild(implicit _project: Project) {

  val addonsJar = s"target/de.tototec.sbuild.addons-${SBuildConfig.sbuildVersion}.jar"

  val compileCp =
    s"mvn:org.scala-lang:scala-library:${SBuildConfig.scalaVersion}" ~
      s"../de.tototec.sbuild/target/de.tototec.sbuild-${SBuildConfig.sbuildVersion}.jar"
  //      ("mvn: org.scalatest:scalatest_" + scalaVersion + ":1.6.1") ~
  //      ("http://cloud.github.com/downloads/KentBeck/junit/junit-4.10.jar") ~
  //      ("http://cloud.github.com/downloads/KentBeck/junit/junit-4.10-src.jar")
  //      "mvn:biz.aQute:bndlib:1.50.0"

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

      addons.scala.Scalac(
        deprecation = true, unchecked = true, debugInfo = "vars", target = "jvm-1.6",
        compilerClasspath = ctx.fileDependencies.filter(f => compilerFilter.pattern.matcher(f.getName).matches),
        srcDir = Path(input),
        destDir = Path(output),
        classpath = ctx.fileDependencies
      )
    }
  }
  Target(addonsJar) dependsOn ("compile") exec { ctx: TargetContext =>
    val jarTask = new AntJar(destFile = ctx.targetFile.get, baseDir = Path("target/classes"))
    jarTask.addFileset(AntFileSet(dir = Path("."), includes = "LICENSE.txt"))
    jarTask.execute
  }

  Target("phony:checkScalaSources") exec { ctx: TargetContext =>
    IfNotUpToDate(Path("src/main/scala"), Path("target"), ctx) {}
  }

  Target("phony:scaladoc") dependsOn SBuildConfig.compilerPath ~ compileCp ~ "checkScalaSources" exec {
    addons.scala.Scaladoc(
      scaladocClasspath = SBuildConfig.compilerPath.files,
      classpath = compileCp.files,
      srcDir = Path("src/main/scala") ,
      destDir = Path("target/scaladoc"),
      deprecation = true, unchecked = true, implicits = true,
      docVersion = SBuildConfig.sbuildVersion,
      docTitle = s"SBuild Addons"
    )
  }

}
