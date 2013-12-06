import de.tototec.sbuild._

@version("0.6.0.9004")
@classpath("../de.tototec.sbuild.plugins/target/de.tototec.sbuild.plugins-0.6.0.9004.jar",
  "../../aether/org.sbuild.plugins.aether/target/org.sbuild.plugins.aether-0.0.9000.jar"
)
class SBuild(implicit _project: Project) {

  Plugin[org.sbuild.plugins.aether.Aether] configure { c =>
    c.scopeDeps += "compile" -> Seq(
      "org.slf4j:slf4j-api:1.7.5"
    )
    c.scopeDeps += "test" -> Seq(
      "compile",
      "org.testng:testng:6.8",
      "ch.qos.logback:logback-classic:1.0.11"
    )
  }

  Plugin[plugins.clean.Clean]

  Plugin[plugins.javac.Javac] configure { c =>
    c.classpath = "aether:compile"
  }
  Plugin[plugins.jar.Jar] configure { c =>
    c.jarName = "hello.jar"
  }

  Plugin[plugins.javac.Javac]("test") configure { c =>
    c.classpath = "target/hello.jar" ~ "aether:test"
  }
  Plugin[plugins.jar.Jar]("test") configure { c =>
    c.jarName = "hello-test.jar"
    c.baseDir = Path("target/test-classes")
    c.dependsOn = "compile-test"
  }

  Target("phony:all") dependsOn "jar"

}
