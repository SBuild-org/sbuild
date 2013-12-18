import de.tototec.sbuild._

@version("0.7.0")
@include("../SBuildConfig.scala")
@classpath(
  "mvn:org.apache.ant:ant:1.8.4",
  "../../maven-deploy/org.sbuild.plugins.mavendeploy/target/org.sbuild.plugins.mavendeploy-0.0.9000.jar"
)
class Test(implicit _project: Project) {

  val version = "0.7.0" // SBuildConfig.version
  val repoUsername = None
  val repoPassword = None

  case class Artifact(id: String, artifactId: String, name: String, description: String)

  val artifacts = Seq(
    Artifact("core", "de.tototec.sbuild", "SBuild Core", "SBuild Core API"),
    Artifact("runner", "de.tototec.sbuild.runner", "SBuild Runner", "SBuild runner and embedded API"),
    Artifact("addons", "de.tototec.sbuild.addons", "SBuild Addons", "SBuild Addons"),
    Artifact("ant", "de.tototec.sbuild.ant", "SBuild Ant Support and Wrappers", ""),
    Artifact("compilerplugin", "de.tototec.sbuild.compilerplugin", "SBuild Scalac Compiler Plugin", "SBuild Scalac Compiler Plugin"),
    Artifact("scriptcompiler", "de.tototec.sbuild.scriptcompiler", "SBuild Script Compiler", "SBuild Script Compiler")
  )

  import org.sbuild.plugins.mavendeploy._

  artifacts map { a =>
    Plugin[MavenDeploy](a.id) configure { p =>
      p.repository = Repository.SonatypeOss.copy(username = repoUsername, password = repoPassword)
      p.licenses = Seq(License.Apache20)
      p.gpg = true
      p.artifact = s"de.tototec:${a.artifactId}:${version}"
      p.files = Map(
        "jar" -> Path[SBuildConfig.type](a.artifactId) / "target" / s"${a.artifactId}-${version}.jar",
        "sources" -> Path[SBuildConfig.type](a.artifactId) / "target" / s"${a.artifactId}-${version}-sources.jar",
        "javadoc" -> "target/fake-javadoc.jar"
      )
    }
  }

  Target("target/fake-javadoc.jar") exec { ctx: TargetContext =>
    import de.tototec.sbuild.ant._
    import de.tototec.sbuild.ant.tasks._
    AntJar(
      destFile = ctx.targetFile.get,
      fileSet = AntFileSet(file = Path[SBuildConfig.type]("LICENSE.txt"))
    )
  }

}
