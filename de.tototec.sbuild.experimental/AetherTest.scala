import de.tototec.sbuild._
import de.tototec.sbuild.TargetRefs._
import de.tototec.sbuild.ant._
import de.tototec.sbuild.ant.tasks._

@version("0.5.0")
@classpath("target/de.tototec.sbuild.experimental.aether-0.5.0.9000.jar")
class AetherTest(implicit _project: Project) {

  Target("phony:prepare") dependsOn
    experimental.aether.AetherSchemeHandler.fullAetherCp ~ "target/de.tototec.sbuild.experimental.aether.impl-0.5.0.9000.jar" exec {
    ctx: TargetContext =>
      val aetherResolver = new experimental.aether.AetherSchemeHandler(aetherClasspath = ctx.fileDependencies)
      SchemeHandler("aether", aetherResolver)
  }

  val testCp =
    "aether:" +
    "org.testng:testng:5.6;classifier=jdk15," +
    "ch.qos.logback:logback-classic:1.0.9"

  Target("phony:resolveViaAether") dependsOn "prepare" ~ testCp exec {
    println("Resolved classpath: " + testCp.files)
  }

}
