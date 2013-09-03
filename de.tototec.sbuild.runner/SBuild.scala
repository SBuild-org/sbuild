import de.tototec.sbuild._
import de.tototec.sbuild.ant._
import de.tototec.sbuild.ant.tasks._
import de.tototec.sbuild.TargetRefs._

@version("0.4.0")
@include("../SBuildConfig.scala")
@classpath("mvn:org.apache.ant:ant:1.8.4")
class SBuild(implicit _project: Project) {

  val namespace = "de.tototec.sbuild.runner"
  val jar = s"target/${namespace}-${SBuildConfig.sbuildVersion}.jar"
  val sourcesZip = s"target/${namespace}-${SBuildConfig.sbuildVersion}-sources.jar"

  val compileCp =
    SBuildConfig.scalaLibrary ~
    SBuildConfig.jansi ~
    SBuildConfig.cmdOption ~
    s"../de.tototec.sbuild/target/de.tototec.sbuild-${SBuildConfig.sbuildVersion}.jar"

  val testCp = compileCp ~
      s"mvn:org.scalatest:scalatest_${SBuildConfig.scalaBinVersion}:1.9.1" ~
      SBuildConfig.scalaActors

  ExportDependencies("eclipse.classpath", testCp)

  Target("phony:all") dependsOn jar ~ sourcesZip ~ "test"

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
      unchecked = true, deprecation = true, debugInfo = "vars"
    )

  }

  Target(jar) dependsOn "compile" ~ "LICENSE.txt" exec { ctx: TargetContext =>
    new AntJar(destFile = ctx.targetFile.get, baseDir = Path("target/classes")) {
      // if (Path("src/main/resources").exists) add(AntFileSet(dir = Path("src/main/resources")))
      add(AntFileSet(file = Path("LICENSE.txt")))
    }.execute
  }

  Target(sourcesZip) dependsOn "scan:src/main" ~ "scan:LICENSE.txt" exec { ctx: TargetContext =>
    AntZip(destFile = ctx.targetFile.get, fileSets = Seq(
      AntFileSet(dir = Path("src/main/scala")),
      // AntFileSet(dir = Path("src/main/resources")),
      AntFileSet(file = Path("LICENSE.txt"))
    ))
  }

  Target("phony:testCompile").cacheable dependsOn SBuildConfig.compilerPath ~ testCp ~ jar ~ "scan:src/test/scala" exec {
    addons.scala.Scalac(
      compilerClasspath = SBuildConfig.compilerPath.files,
      classpath = testCp.files ++ jar.files,
      sources = "scan:src/test/scala".files,
      destDir = Path("target/test-classes"),
      deprecation = true, unchecked = true, debugInfo = "vars"
    )
  }

  Target("phony:test") dependsOn testCp ~ jar ~ "testCompile" exec {
    addons.scalatest.ScalaTest(
      classpath = testCp.files ++ jar.files,
      runPath = Seq("target/test-classes"),
      reporter = "oF",
      fork = true)
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
