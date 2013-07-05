import de.tototec.sbuild._
import de.tototec.sbuild.TargetRefs._
import de.tototec.sbuild.ant._
import de.tototec.sbuild.ant.tasks._

@version("0.4.0")
@classpath(
  "mvn:org.apache.ant:ant:1.8.3",
  "../../../../target/de.tototec.sbuild.addons-0.4.0.9002.jar"
)
class SBuild(implicit project: Project) {

  var jar = "target/de.tototec.sbuild.addons.junit.test.jar"

  val testCp = "mvn:junit:junit:4.10" ~
    jar

  Target("phony:clean").evictCache dependsOn "clean-classes" exec {
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

  val sources = "scan:src/main/java"

  var classesDep = classes.map(c => TargetRefs(c._1)).reduceLeft(_ ~ _)

  Target(sources) dependsOn classesDep

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

  

  Target("phony:compile").cacheable dependsOn sources exec { ctx: TargetContext =>
    addons.java.Javac(
      fork = true, source = "1.6", target = "1.6", debugInfo = "vars",
      sources = sources.files,
      destDir = Path("target/classes")
    )
  }

  Target(jar) dependsOn "compile" exec { ctx: TargetContext =>
    AntJar(destFile = ctx.targetFile.get, baseDir = Path("target/classes"))
  }

  Target("scan:src/test/java") dependsOn classesDep

  Target("phony:testCompile").cacheable dependsOn "scan:src/test/java" ~ testCp exec {
    addons.java.Javac(
        fork = true, source = "1.6", target = "1.6", debugInfo = "vars",
        classpath = testCp.files,
        sources = "scan:src/test/java".files,
        destDir = Path("target/test-classes")
    )
  }

  Target("phony:test") dependsOn testCp ~ "testCompile" exec {
    addons.junit.JUnit(
      classpath = testCp.files ++ Seq(Path("target/test-classes")),
      classes = Seq("de.tototec.sbuild.addons.junit.test.TestClassTest"),
      failOnError = false
    )
  }

}
