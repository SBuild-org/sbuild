import de.tototec.sbuild._
import org.apache.tools.ant.taskdefs._
import org.apache.tools.ant

class SBuild(implicit P: Project) {

  Target("phony:clean") exec {
    new Delete() {
      setDir(Path("target"))
    }.execute
  }

  Target("phony:all") dependsOn "target/test.jar"

  SchemeHandler("mvn", new MvnSchemeHandler(".m2/repository"))

  Target("phony:compile") exec {
    new Mkdir() {
      setDir(Path("target/classes"))
    }.execute

    new Javac() {
      setProject(P.ant)
      setFork(true)
      setSrcdir(new ant.types.Path(P.ant) {setLocation(Path("src/main/java"))})
      setDestdir(P.uniqueFile("target/classes"))
    }.execute
  } 

  Target("target/test.jar") dependsOn "compile" exec {
    new Jar() {
      setProject(P.ant)
      // setFilesonly(true)
      setDestFile(Path("target/test.jar"))
      setBasedir(Path("target/classes"))
    }.execute
  }

}
