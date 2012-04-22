import de.tototec.sbuild._
import de.tototec.sbuild.ant.tasks._

@version("0.0.1")
@classpath("http://repo1.maven.org/maven2/org/apache/ant/ant/1.8.3/ant-1.8.3.jar")
class SEcho(implicit project: Project) {

  Target("echo") help "Say hello to the world" exec {
    AntEcho(message = "Hello World!")
  }

}
