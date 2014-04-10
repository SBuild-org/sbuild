import de.tototec.sbuild._
import de.tototec.sbuild.ant._
import de.tototec.sbuild.ant.tasks._
import de.tototec.sbuild.TargetRefs._

@version("0.4.0")
@include("../SBuildConfig.scala")
@classpath("mvn:org.apache.ant:ant:1.8.4")
class SBuild(implicit _project: Project) {

  val namespace = "org.sbuild.runner"
  val jar = s"target/${namespace}-${SBuildConfig.sbuildVersion}.jar"
  val sourcesZip = s"target/${namespace}-${SBuildConfig.sbuildVersion}-sources.jar"

  val testJar = s"target/${namespace}-${SBuildConfig.sbuildVersion}-tests.jar"

  val compileCp =
    SBuildConfig.scalaLibrary ~
      SBuildConfig.jansi ~
      SBuildConfig.cmdOption ~
      s"../org.sbuild/target/org.sbuild-${SBuildConfig.sbuildVersion}.jar" ~
      SBuildConfig.scalaXml ~
      SBuildConfig.sbuildUnzipPlugin

  val testCp = compileCp ~
    SBuildConfig.scalaTest

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
        unchecked = true, deprecation = true, debugInfo = "vars",
        fork = true
      )

    }

  Target(jar) dependsOn "compile" ~ "compile-messages" ~ "LICENSE.txt" ~ "scan:src/main/resources" exec { ctx: TargetContext =>
    new AntJar(destFile = ctx.targetFile.get, baseDir = Path("target/classes"),
      manifestEntries = Map("I18n-Catalog" -> "org.sbuild.runner.SBuildMessages")
    ) {
      if (Path("src/main/resources").exists) add(AntFileSet(dir = Path("src/main/resources")))
      if (Path("target/po-classes").exists) add(AntFileSet(dir = Path("target/po-classes")))
      add(AntFileSet(file = Path("LICENSE.txt")))
    }.execute
  }

  Target(testJar) dependsOn "testCompile" ~ "scan:target/test-classes" exec { ctx: TargetContext =>
    AntJar(destFile = ctx.targetFile.get, baseDir = Path("target/test-classes"))
  }

  Target(sourcesZip) dependsOn "scan:src/main" ~ "scan:LICENSE.txt" exec { ctx: TargetContext =>
    AntZip(destFile = ctx.targetFile.get, fileSets = Seq(
      AntFileSet(dir = Path("src/main/scala")),
      AntFileSet(dir = Path("src/main/resources")),
      AntFileSet(dir = Path("src/main/po")),
      AntFileSet(file = Path("LICENSE.txt"))
    ))
  }

  Target("phony:testCompile").cacheable dependsOn SBuildConfig.compilerPath ~ testCp ~ jar ~ "scan:src/test/scala" exec {
    addons.scala.Scalac(
      compilerClasspath = SBuildConfig.compilerPath.files,
      classpath = testCp.files ++ jar.files,
      sources = "scan:src/test/scala".files,
      destDir = Path("target/test-classes"),
      deprecation = true, unchecked = true, debugInfo = "vars",
      fork = true
    )
  }

  Target("phony:test") dependsOn testCp ~ jar ~ "testCompile" exec {
    //    addons.scalatest.ScalaTest(
    //      classpath = testCp.files ++ jar.files,
    //      runPath = Seq("target/test-classes"),
    //      //      reporter = "oF",
    //      standardOutputSettings = "FD",
    //      xmlOutputDir = Path("target/test-output"),
    //      fork = true)

    val res = addons.support.ForkSupport.runJavaAndWait(
      classpath = testCp.files ++ jar.files,
      arguments = Array("org.scalatest.tools.Runner", "-p", Path("target/test-classes").getPath, "-oG", "-u", Path("target/test-output").getPath)
    )
    if (res != 0) throw new RuntimeException("Some tests failed")

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

  val i18n = new I18n()
  i18n.targetCatalogDir = Path("target/po-classes/org/sbuild/runner")
  i18n.applyAll

}
