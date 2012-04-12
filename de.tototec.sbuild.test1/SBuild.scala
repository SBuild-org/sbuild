import de.tototec.sbuild._
import de.tototec.sbuild.ant._
import org.apache.tools.ant.taskdefs._

// @classpath("http://repo1.maven.org/maven2/org/apache/ant/ant/1.8.3/ant-1.8.3.jar")
@classpath(
  "http://repo1.maven.org/maven2/org/apache/ant/ant/1.8.3/ant-1.8.3.jar",
  "/home/lefou/work/tototec/sbuild/de.tototec.sbuild.ant/target/de.tototec.sbuild.ant-0.0.1-SNAPSHOT.jar"
)
class SBuild(implicit P: Project) {

  // SchemeHandler("mvn", new MvnSchemeHandler("/home/lefou/.m2/repository-tototec"))
  // SchemeHandler("http", new HttpSchemeHandler(".sbuild/http"))

  Target("phony:clean") exec {
    new Delete() { 
      setProject(AntProject())
      setDir(Path("target"))
    }.execute
  } help "Clean all output (target dir)"

  Target("phony:all") dependsOn "target/test.jar" help "Build the project"

  // Idea: MavenLikeJavaProject()

  Target("phony:compile") exec {
    new Mkdir() {
      setProject(AntProject())
      setDir(Path("target/classes"))
    }.execute

    new Javac() { 
      setProject(AntProject())
      setFork(true)
      setSource("1.6"); setTarget("1.6")
      setDebug(Prop("java.debug", "true").toBoolean)
      setIncludeantruntime(false)
      setSrcdir(AntPath("src/main/java"))
      setDestdir(Path("target/classes"))
    }.execute
  } 

  Target("target/test.jar") dependsOn "compile" exec { ctx: TargetContext =>
    new Jar() {
      setProject(AntProject())
      setDestFile(ctx.targetFile.get)
      setBasedir(Path("target/classes"))
    }.execute
  }

}
