import de.tototec.sbuild._
import de.tototec.sbuild.ant._
import org.apache.tools.ant.taskdefs._

@classpath("/home/lefou/.m2/repository-tototec/org/apache/ant/ant/1.8.2/ant-1.8.2.jar")
class SBuild(implicit P: Project) {

  Prop("java.source", "1.6")
  Prop("java.target", "1.6")
  Prop("java.debug", "true")
  Prop("java.source.dir", "src/main/java")
  Prop("java.output.dir", "target/classes")

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
      setDir(Path(Prop("java.output.dir")))
    }.execute

    new Javac() { 
      setProject(AntProject())
      setFork(true)
      setSource(Prop("java.source"))
      setTarget(Prop("java.target"))
      setDebug(Prop("java.debug").toBoolean)
      setIncludeantruntime(false)
      setSrcdir(AntPath(Prop("java.source.dir")))
      setDestdir(Path(Prop("java.output.dir")))
    }.execute
  } 

  Target("target/test.jar") dependsOn "compile" exec {
    new Jar() {
      setProject(AntProject())
      setDestFile(Path("target/test.jar"))
      setBasedir(Path(Prop("java.output.dir")))
    }.execute
  }

}
