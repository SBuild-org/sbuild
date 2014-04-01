import de.tototec.sbuild._
import de.tototec.sbuild.TargetRefs._

@version("0.4.0")
class SBuild(implicit _project: Project) {

  val modules = Modules(
    "org.sbuild",
    "org.sbuild.runner",
    "org.sbuild.ant",
    "org.sbuild.addons",
    "org.sbuild.scriptcompiler",
    "org.sbuild.compilerplugin",
    "org.sbuild.experimental",
    "sbuild-dist",
    "sbuild-unzip-plugin/org.sbuild.plugins.unzip",
    "org.sbuild.runner.bootstrap"
  )

  Target("phony:clean") dependsOn modules.map(m => m("clean"))

  Target("phony:all") dependsOn modules.map(m => m("all"))

  Target("phony:test") dependsOn "org.sbuild::test" ~ "org.sbuild.runner::test"

  Target("phony:scaladoc") dependsOn
    "org.sbuild::scaladoc" ~
    "org.sbuild.runner::scaladoc" ~
    "org.sbuild.ant::scaladoc" ~
    "org.sbuild.addons::scaladoc" ~
    "org.sbuild.compilerplugin::scaladoc" ~
    "org.experimental::scaladoc"

  Target("phony:dist") dependsOn "sbuild-dist::dist"

}
