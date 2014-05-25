import de.tototec.sbuild._
import de.tototec.sbuild.ant._
import de.tototec.sbuild.ant.tasks._
import de.tototec.sbuild.TargetRefs._

@version("0.4.0")
@include("../SBuildConfig.scala")
@classpath("mvn:org.apache.ant:ant:1.8.4")
class SBuild(implicit _project: Project) {

  val namespace = "org.sbuild.runner.bootstrap"
  val jar = s"target/${namespace}-${SBuildConfig.sbuildVersion}.jar"
  val sourcesZip = s"target/${namespace}-${SBuildConfig.sbuildVersion}-sources.jar"

  val compileCp =
    SBuildConfig.scalaLibrary ~
      s"../org.sbuild/target/org.sbuild-${SBuildConfig.sbuildVersion}.jar" ~
      s"../org.sbuild.runner/target/org.sbuild.runner-${SBuildConfig.sbuildVersion}.jar" ~
      SBuildConfig.sbuildUnzipPlugin ~
      SBuildConfig.sbuildHttpPlugin ~
      SBuildConfig.sbuildSourceSchemePlugin

  val testCp = compileCp ~
    SBuildConfig.scalaTest

  ExportDependencies("eclipse.classpath", testCp)

  Target("phony:all") dependsOn jar ~ sourcesZip

  Target("phony:clean").evictCache exec {
    AntDelete(dir = Path("target"))
  }

  Target("phony:compile").cacheable dependsOn SBuildConfig.compilerPath ~ compileCp ~
    "scan:src/main/scala" exec {

      val output = "target/classes"

      // compile scala files
      addons.scala.Scalac(
        compilerClasspath = SBuildConfig.compilerPath.files,
        classpath = compileCp.files,
        sources = "scan:src/main/scala".files,
        destDir = Path(output),
        unchecked = true, deprecation = true, debugInfo = "vars",
        fork = true
      )

    }

  Target(jar) dependsOn "compile" ~ "LICENSE.txt" ~ "scan:src/main/resources" exec { ctx: TargetContext =>
    new AntJar(destFile = ctx.targetFile.get, baseDir = Path("target/classes"),
      manifestEntries = Map("I18n-Catalog" -> "org.sbuild.runner.SBuildMessages")
    ) {
      add(AntFileSet(file = Path("LICENSE.txt")))
    }.execute
  }

  Target(sourcesZip) dependsOn "scan:src/main" ~ "scan:LICENSE.txt" exec { ctx: TargetContext =>
    AntZip(destFile = ctx.targetFile.get, fileSets = Seq(
      AntFileSet(dir = Path("src/main/scala")),
      AntFileSet(file = Path("LICENSE.txt"))
    ))
  }

  Target("phony:scaladoc").cacheable dependsOn SBuildConfig.compilerPath ~ compileCp ~ "scan:src/main/scala" exec {
    addons.scala.Scaladoc(
      scaladocClasspath = SBuildConfig.compilerPath.files,
      classpath = compileCp.files,
      sources = "scan:src/main/scala".files,
      destDir = Path("target/scaladoc"),
      deprecation = true, unchecked = true, implicits = true,
      docVersion = SBuildConfig.sbuildVersion,
      docTitle = s"SBuild Runner API Reference"
    )
  }

}
