package de.tototec.sbuild.plugins

import de.tototec.sbuild.Plugin
import de.tototec.sbuild.TargetRefs
import de.tototec.sbuild.TargetRefs._
import de.tototec.sbuild.Project
import de.tototec.sbuild.Target
import de.tototec.sbuild.Util
import de.tototec.sbuild.Path
import de.tototec.sbuild.addons.java.Javac

class JavaPlugin()(implicit _project: Project) extends Plugin {

  var classesDir = "target/classes"
  var testClassesDir = "target/test-classes"
  var compileCp: TargetRefs = "compileCp"
  var testCompileCp: TargetRefs = "testCompileCp"

  override def init {

    // TODO: refactor targets out for later use

    val cleanJavaClasses = Target("phony:cleanJavaClasses") evictCache "compileMainJava" exec {
      Util.delete(Path(classesDir))
    }

    Target("phony:clean") dependsOn cleanJavaClasses

    Target("phony:compileJava").cacheable exec {

    }

    val cleanJavaTestClasses = Target("phony:cleanJavaTestClasses") evictCache "compileTestJava" exec {
      Util.delete(Path(testClassesDir))
    }
    Target("phony:clean") dependsOn cleanJavaTestClasses

    Target("phony:compileTestjava").cacheable exec {

    }

  }

}