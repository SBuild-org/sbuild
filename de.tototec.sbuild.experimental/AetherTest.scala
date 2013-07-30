import de.tototec.sbuild._
import de.tototec.sbuild.TargetRefs._
import de.tototec.sbuild.ant._
import de.tototec.sbuild.ant.tasks._

@version("0.5.0")
@classpath("target/de.tototec.sbuild.experimental.aether-0.5.0.9001.jar")
class AetherTest(implicit _project: Project) {

  import experimental.aether.AetherSchemeHandler

  //val aetherCp = AetherSchemeHandler.fullAetherCp ~ "target/de.tototec.sbuild.experimental.aether.impl-0.5.0.9000.jar"
  // println("aetherCp = " + aetherCp)

  // val aetherCpFiles = de.tototec.sbuild.ResolveFiles(aetherCp)
  // println("aetherCpFiles = " + aetherCpFiles)

  val aetherSchemeHandler = AetherSchemeHandler.resolveAndCreate()
  SchemeHandler("aether", aetherSchemeHandler)

  val testCp =
    "aether:" +
    "org.testng:testng:6.8.1," +
    "org.testng:testng:6.0," +
    "ch.qos.logback:logback-classic:1.0.11"

  Target("phony:resolveViaAether") dependsOn testCp exec {
    println("Resolved classpath: " + testCp.files)
  }

}
