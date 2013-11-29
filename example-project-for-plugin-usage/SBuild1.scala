import de.tototec.sbuild._

@version("0.6.0.9004")
@classpath("../de.tototec.sbuild.plugins/target/de.tototec.sbuild.plugins-0.6.0.9004.jar",
  "../../aether/de.tototec.sbuild.addons.aether/target/de.tototec.sbuild.addons.aether-0.0.9000.jar"
)
class SBuild(implicit _project: Project) {

  plugins.javaproject.JavaProject(
    name = "hello",
    version = "0.0.1",
    source = Some("1.6"),
    target = Some("1.6")
)

  Plugin[addons.aether.Aether] configure { c =>
    c.scopeDeps += "compile" -> Seq(
      "org.slf4j:slf4j-api:1.7.5"
    )
    c.scopeDeps += "test" -> Seq(
      "compile",
      "org.testng:testng:6.8",
      "ch.qos.logback:logback-classic:1.0.11"
    )
  }

  Target("phony:compileCp") dependsOn "aether:compile"
  Target("phony:testCp") dependsOn "aether:test"

}
