package de.tototec.sbuild.plugins.javaproject

import de.tototec.sbuild.Path
import de.tototec.sbuild.Plugin
import de.tototec.sbuild.Project
import de.tototec.sbuild.Target
import de.tototec.sbuild.TargetRefs._
import de.tototec.sbuild.plugins.clean.Clean
import de.tototec.sbuild.plugins.jar.Jar
import de.tototec.sbuild.plugins.javac.Javac

object JavaProject {

  def apply(name: String,
            version: String,
            source: Option[String] = None,
            target: Option[String] = None,
            deprecation: Option[Boolean] = Some(true),
            debugInfo: Option[String] = Some("vars"),
            aether: Boolean = false)(implicit project: Project) {

    val compileCp = Target("phony:compileCp")
    val testCp = Target("phony:testCp")
    val runtimeCp = Target("phony:runtimeCp")
    
    val jarName = s"$name-$version.jar"
    val testJarName = s"$name-$version-tests.jar"
    val sourceJarName = s"name-$version-sources.jar"
    val javadocJarName = s"$name-$version-javadoc.jar"

    val javacConfigurer = { c: Javac =>
      c.source = source
      c.target = target
      c.deprecation = deprecation
      c.debugInfo = debugInfo
    }

    Plugin[Clean]

    Plugin[Javac] configure javacConfigurer
    Plugin[Javac] configure { c =>
      c.classpath = compileCp
    }

    Plugin[Jar] configure { c =>
      c.jarName = jarName
    }

    Plugin[Javac]("test") configure javacConfigurer
    Plugin[Javac]("test") configure { c =>
      c.classpath = s"target/$jarName" ~ "aether:test"
    }

    Plugin[Jar]("test") configure { c =>
      c.jarName = testJarName
      c.baseDir = Path("target/test-classes")
      c.dependsOn = "compile-test"
      c.aggregatorTarget = Some("phony:jar-test")
    }

    Target("phony:all") dependsOn "jar"
  }
}