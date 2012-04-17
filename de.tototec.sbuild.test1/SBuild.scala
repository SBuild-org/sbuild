import de.tototec.sbuild._
import de.tototec.sbuild.ant._
import de.tototec.sbuild.ant.tasks._
import org.apache.tools.ant.taskdefs._

@classpath("http://repo1.maven.org/maven2/org/apache/ant/ant/1.8.3/ant-1.8.3.jar")
class SBuild(implicit P: Project) {

  // SchemeHandler("mvn", new MvnSchemeHandler(Path("/home/lefou/.m2/repository-tototec")))
  // SchemeHandler("http", new HttpSchemeHandler(Path(".sbuild/http")))

  Target("phony:clean") exec {
    AntDelete(dir = Path("target")) 
  } help "Clean all output (target dir)"

  Target("phony:all") dependsOn "target/test.jar" help "Build the project"

  // Idea: MavenLikeJavaProject()

  Target("phony:compile") exec {
    AntMkdir(dir = "target/classes")
    AntJavac(
      fork = true,
      source = "1.6",
      target = "1.6",
      debug = Prop("java.debug", "true").toBoolean,
      includeAntRuntime = false,
      srcDir = AntPath("src/main/java"),
      destDir = Path("target/classes")
    )
  } 

  Target("target/test.jar") dependsOn "compile" exec { ctx: TargetContext =>
    AntJar(destFile = ctx.targetFile.get, baseDir = Path("target/classes"))
  }

}
