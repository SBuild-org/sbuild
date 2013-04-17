package de.tototec.sbuild.plugins

import de.tototec.sbuild.Plugin
import de.tototec.sbuild.TargetRef
import de.tototec.sbuild.TargetRefs
import de.tototec.sbuild.TargetRefs._
import de.tototec.sbuild.Project
import de.tototec.sbuild.Target
import de.tototec.sbuild.Util
import de.tototec.sbuild.Util.NullSafe
import de.tototec.sbuild.Path
import de.tototec.sbuild.addons.java.Javac

class JavaPlugin()(implicit _project: Project) extends Plugin {

  // compile

  var compileCp: TargetRefs = null
  var testCp: TargetRefs = null

  var sourceDirs: Seq[String] = Seq("src/main/java")
  var testSourceDirs: Seq[String] = Seq("src/test/java")

  var additionalSources: TargetRefs = TargetRefs()
  var additionalTestSources: TargetRefs = TargetRefs()

  var compileDependsOn: TargetRefs = TargetRefs()
  var testDependsOn: TargetRefs = TargetRefs()
  
  var classesDir = "target/classes"
  var testClassesDir = "target/test-classes"

  var source: String = null
  var sourcePattern: String = null
  var target: String = null
  var encoding: String = "UTF-8"
  var javacCacheable: Boolean = true

  var javacCustomizer: Javac => Unit = null
  var testJavacCustomizer: Javac => Unit = null

  
  
  // Test Compile

  override def init {
    // ensure these deps are defined and the targets exists
    compileCp = compileCp whenNull Target("phony:compileCp")
    testCp = testCp whenNull Target("phony:testCp")

    // TODO: refactor targets out for later use
    initCompileJava
    initCompileTestJava
    initCleanJava
    initCleanTestJava
  }

  protected def initCompileJava {

    val sources: TargetRefs = sourceDirs.map { dir => TargetRef("scan:" + dir) }

    val t = Target("phony:compileJava").cacheable dependsOn 
      compileDependsOn ~ compileCp ~ sources ~ additionalSources exec {
      val javac = new Javac(
        classpath = compileCp.files,
        sources = sources.files ++ additionalSources.files,
        destDir = Path(classesDir),
        source = source,
        target = target,
        encoding = encoding
      )
      javacCustomizer.notNull(c => c(javac))
      javac.execute
    }

    Target("phony:compile") dependsOn t
  }

  protected def initCleanJava {
    val cleanJavaClasses = Target("phony:cleanJavaClasses") evictCache "compileJava" exec {
      Util.delete(Path(classesDir))
    }
    Target("phony:clean") dependsOn cleanJavaClasses
  }

  protected def initCompileTestJava {
    val sources: TargetRefs = testSourceDirs.map { dir => TargetRef("scan:" + dir) }

    val t = Target("phony:compileTestJava").cacheable dependsOn 
      testDependsOn ~ testCp ~ "compile" ~ sources ~ additionalTestSources exec {
      val sourceFiles = sources.files ++ additionalTestSources.files
      if (!sourceFiles.isEmpty) {
        val javac = new Javac(
          classpath = testCp.files,
          sources = sourceFiles,
          destDir = Path(testClassesDir),
          source = source,
          target = target,
          encoding = encoding
        )
        testJavacCustomizer.notNull(c => c(javac))
        javac.execute
      }
      else {
        println("No test sources found")
      }
    }

    Target("phony:testCompile") dependsOn t
  }

  protected def initCleanTestJava {
    val cleanJavaTestClasses = Target("phony:cleanJavaTestClasses") evictCache "compileTestJava" exec {
      Util.delete(Path(testClassesDir))
    }
    Target("phony:clean") dependsOn cleanJavaTestClasses
  }

}
