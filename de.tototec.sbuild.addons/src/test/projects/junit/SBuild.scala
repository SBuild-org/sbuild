import de.tototec.sbuild._
import de.tototec.sbuild.TargetRefs._
import de.tototec.sbuild.ant._
import de.tototec.sbuild.ant.tasks._

@version("0.1.0.9002")
@classpath(
  "http://repo1.maven.org/maven2/org/apache/ant/ant/1.8.3/ant-1.8.3.jar",
  "../../../../target/de.tototec.sbuild.addons.jar"
)
class SBuild(implicit project: Project) {

  var jar = "target/de.tototec.sbuild.addons.junit.test.jar"

  SchemeHandler("mvn", new MvnSchemeHandler())

  val testCp = "mvn:junit:junit:4.10" ~
    jar

  Target("phony:clean") dependsOn "clean-classes" exec {
    AntDelete(dir = Path("target"))
    AntDelete(dir = Path("src"))
  }

  Target("phony:all") dependsOn jar

  val classes = Seq(
    ("src/main/java/de/tototec/sbuild/addons/junit/test/TestClass.java", """
package de.tototec.sbuild.addons.junit.test;
public class TestClass {
  public String hello() {
    return "hello";
  }
}"""),
    ("src/test/java/de/tototec/sbuild/addons/junit/test/TestClassTest.java", """
package de.tototec.sbuild.addons.junit.test;
import org.junit.Test;
import org.junit.Ignore;
import static org.junit.Assert.*;
public class TestClassTest {
  @Test
  public void testHello() {
    TestClass testClass = new TestClass();
    assertEquals("hello", testClass.hello());
  }
  @Test
  public void testHelloFail() {
    TestClass testClass = new TestClass();
    assertEquals("HELLO", testClass.hello());
  }
  @Test
  @Ignore
  public void testHelloIgnore() {
    TestClass testClass = new TestClass();
    assertEquals("HELLO", testClass.hello());
  }
}"""))

  var classesDep = classes.map(c => TargetRefs(c._1)).reduceLeft(_ ~ _)

  classes foreach { 
    case (file, content) => 
      Target(file) dependsOn project.projectFile exec {
        AntEcho(file = Path(file), message = content)
      }
  }

  Target("phony:clean-classes") exec {
    classes foreach {
      case (file, _) => AntDelete(file = Path(file))
    }
  }

  Target("phony:compile") dependsOn classesDep exec { ctx: TargetContext =>
    IfNotUpToDate(Path("src/main/java"), Path("target"), ctx) {
      AntMkdir(dir = Path("target/classes"))
      AntJavac(
        fork = true,
        source = "1.6",
        target = "1.6",
        debug = true,
        includeAntRuntime = false,
        classpath = AntPath(locations = ctx.fileDependencies),
        srcDir = AntPath("src/main/java"),
        destDir = Path("target/classes")
      )
    }
  }

  Target(jar) dependsOn "compile" exec { ctx: TargetContext =>
    AntJar(destFile = ctx.targetFile.get, baseDir = Path("target/classes"))
  }

  Target("phony:testCompile") dependsOn classesDep ~ testCp exec { ctx: TargetContext =>
    IfNotUpToDate(Path("src/test/java"), Path("target"), ctx) {
      AntMkdir(dir = Path("target/test-classes"))
      AntJavac(
        fork = true,
        source = "1.6",
        target = "1.6",
        debug = true,
        includeAntRuntime = false,
        classpath = AntPath(locations = ctx.fileDependencies),
        srcDir = AntPath("src/test/java"),
        destDir = Path("target/test-classes")
      )
    }
  }

  Target("phony:test") dependsOn testCp ~ "testCompile" exec { ctx: TargetContext =>
    addons.junit.JUnit(
      classpath = ctx.fileDependencies ++ Seq(Path("target/test-classes")),
      classes = Seq("de.tototec.sbuild.addons.junit.test.TestClassTest"),
      failOnError = false
    )
  }

}
