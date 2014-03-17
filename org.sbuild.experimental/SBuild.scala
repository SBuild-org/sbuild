import de.tototec.sbuild._
import de.tototec.sbuild.ant._
import de.tototec.sbuild.ant.tasks._
import de.tototec.sbuild.TargetRefs._

@version("0.4.0")
@include("../SBuildConfig.scala")
@classpath("mvn:org.apache.ant:ant:1.8.4")
class SBuild(implicit _project: Project) {

  import SBuildConfig.{ sbuildVersion, compilerPath, scalaVersion }

  val namespace = "org.sbuild.experimental"
  val jar = s"target/${namespace}-${sbuildVersion}.jar"
  val sourcesZip = s"target/${namespace}-${sbuildVersion}-sources.jar"

  val compileCp =
    s"mvn:org.scala-lang:scala-library:${scalaVersion}" ~
      s"../org.sbuild/target/org.sbuild-${sbuildVersion}.jar"

  ExportDependencies("eclipse.classpath", compileCp)

  val sources = "scan:src/main/scala"

  Target("phony:all") dependsOn jar ~ sourcesZip

  Target("phony:clean").evictCache exec {
    AntDelete(dir = Path("target"))
  }

  Target("phony:compile").cacheable dependsOn SBuildConfig.compilerPath ~ compileCp ~ sources exec {
    val output = "target/classes"
    addons.scala.Scalac(
      compilerClasspath = compilerPath.files,
      classpath = compileCp.files,
      sources = sources.files,
      destDir = Path(output),
      unchecked = true, deprecation = true, debugInfo = "vars",
      fork = true
    )
  }

  Target(jar) dependsOn "compile" ~ "scan:src/main/resources" ~ "LICENSE.txt" exec { ctx: TargetContext =>
    new AntJar(destFile = ctx.targetFile.get, baseDir = Path("target/classes")) {
      if (Path("src/main/resources").exists) add(AntFileSet(dir = Path("src/main/resources")))
      add(AntFileSet(file = Path("LICENSE.txt")))
    }.execute
  }

  Target(sourcesZip) dependsOn "scan:src/main/scala" ~ "scan:LICENSE.txt" exec { ctx: TargetContext =>
    AntZip(destFile = ctx.targetFile.get, fileSets = Seq(
      AntFileSet(dir = Path("src/main/scala")),
      AntFileSet(file = Path("LICENSE.txt"))
    ))
  }

  Target("phony:scaladoc").cacheable dependsOn compilerPath ~ compileCp ~ sources exec {
    addons.scala.Scaladoc(
      scaladocClasspath = compilerPath.files,
      classpath = compileCp.files,
      sources = sources.files,
      destDir = Path("target/scaladoc"),
      deprecation = true, unchecked = true, implicits = true,
      docVersion = sbuildVersion,
      docTitle = s"SBuild Experimental API Reference"
    )
  }

}
