import de.tototec.sbuild._
import de.tototec.sbuild.TargetRefs._
import de.tototec.sbuild.ant._
import de.tototec.sbuild.ant.tasks._

@version("0.5.0")
@classpath("target/de.tototec.sbuild.experimental.aether-0.5.0.9000.jar",
"mvn:org.fusesource.jansi:jansi:1.10",
"mvn:de.tototec:de.tototec.cmdoption:0.3.0")
class AetherTest(implicit _project: Project) {

  import experimental.aether.AetherSchemeHandler

  val aetherCp = AetherSchemeHandler.fullAetherCp ~ "target/de.tototec.sbuild.experimental.aether.impl-0.5.0.9000.jar"
  // println("aetherCp = " + aetherCp)

  val aetherCpFiles = de.tototec.sbuild.ResolveFiles(aetherCp)
  // println("aetherCpFiles = " + aetherCpFiles)

  SchemeHandler("aether", new AetherSchemeHandler(aetherCpFiles))

  val testCp =
    "aether:" +
    "org.testng:testng:5.6;classifier=jdk15," +
    "ch.qos.logback:logback-classic:1.0.9"

  Target("phony:resolveViaAether") dependsOn testCp exec {
    println("Resolved classpath: " + testCp.files)
  }

}
