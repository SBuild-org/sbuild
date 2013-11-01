import de.tototec.sbuild._
import de.tototec.sbuild.TargetRefs._

@version("0.4.0")
class SBuild(implicit _project: Project) {

  val modules = Modules(
    "de.tototec.sbuild",
    "de.tototec.sbuild.runner",
    "de.tototec.sbuild.ant",
    "de.tototec.sbuild.addons",
    "de.tototec.sbuild.scriptcompiler",
    "de.tototec.sbuild.compilerplugin",
    "de.tototec.sbuild.plugins",
    "de.tototec.sbuild.experimental",
    "doc",
    "sbuild-dist"
  )

  Target("phony:clean") dependsOn modules.map(m => m("clean"))

  Target("phony:all") dependsOn modules.map(m => m("all"))

  Target("phony:test") dependsOn "de.tototec.sbuild::test" ~ "de.tototec.sbuild.runner::test"

  Target("phony:scaladoc") dependsOn
    "de.tototec.sbuild::scaladoc" ~
    "de.tototec.sbuild.runner::scaladoc" ~
    "de.tototec.sbuild.ant::scaladoc" ~
    "de.tototec.sbuild.addons::scaladoc" ~
    "de.tototec.sbuild.compilerplugin::scaladoc" ~
    "de.tototec.sbuild.experimental::scaladoc"
//    "de.tototec.sbuild.plugins::scaladoc"

  Target("phony:dist") dependsOn "sbuild-dist::dist"

}
