import de.tototec.sbuild._
import de.tototec.sbuild.TargetRefs._
import de.tototec.sbuild.ant._
import de.tototec.sbuild.ant.tasks._

@version("0.5.0")
@classpath("target/de.tototec.sbuild.addons.aether-0.5.0.9003.jar")
class AetherTest(implicit _project: Project) {

  SchemeHandler("aether", addons.aether.AetherSchemeHandler.resolveAndCreate())

  val testCp =
    "aether:" +
    "org.testng:testng:6.8.1," +
    "org.testng:testng:6.0," +
    "ch.qos.logback:logback-classic:1.0.11"

  Target("phony:resolveViaAether") dependsOn testCp exec {
    println("Resolved classpath: " + testCp.files)
  }

}
