import de.tototec.sbuild._
import de.tototec.sbuild.ant._
import de.tototec.sbuild.ant.tasks._
import de.tototec.sbuild.TargetRefs._

@version("0.3.0")
@include(
  "../SBuildConfig.scala"
)
@classpath(
  "http://repo1.maven.org/maven2/org/apache/ant/ant/1.8.3/ant-1.8.3.jar"
)
class SBuild(implicit _project: Project) {

  SchemeHandler("http", new HttpSchemeHandler(Path(".sbuild/http")))
  SchemeHandler("mvn", new MvnSchemeHandler())

  val jar = s"target/de.tototec.sbuild.ant-${SBuildConfig.sbuildVersion}.jar"

  // Current version of bnd (with ant tasks) is not in Maven repo 
  val bnd_1_50_0 = "http://dl.dropbox.com/u/2590603/bnd/biz.aQute.bnd.jar"

  val compileCp =
    s"../de.tototec.sbuild/target/de.tototec.sbuild-${SBuildConfig.sbuildVersion}.jar" ~
      s"mvn:org.scala-lang:scala-library:${SBuildConfig.scalaVersion}" ~
      s"mvn:org.scala-lang:scala-compiler:${SBuildConfig.scalaVersion}" ~
      "mvn:org.apache.ant:ant:1.8.3" ~
      "mvn:org.liquibase:liquibase-core:2.0.3" ~
      bnd_1_50_0

  ExportDependencies("eclipse.classpath", compileCp)

  Target("phony:all") dependsOn jar

  val clean = Target("phony:clean") exec {
    AntDelete(dir = Path("target"))
  }

  Target("phony:compile") dependsOn SBuildConfig.compilerPath ~ compileCp exec { ctx: TargetContext =>
    val input = "src/main/scala"
    val output = "target/classes"
    AntMkdir(dir = Path(output))
    IfNotUpToDate(Path(input), Path("target"), ctx) {

      val compilerFilter = """.*scala-((library)|(compiler)|(reflect)).*""".r

      new addons.scala.Scalac(
        deprecation = true, unchecked = true, debugInfo = "vars", target = "jvm-1.5",
        fork = true,
        compilerClasspath = ctx.fileDependencies.filter(f => compilerFilter.pattern.matcher(f.getName).matches),
        srcDir = Path(input),
        destDir = Path(output),
        classpath = ctx.fileDependencies
      ).execute
    }
  }

  Target(jar) dependsOn "compile" exec { ctx: TargetContext =>
    new AntJar(
      destFile = ctx.targetFile.get,
      baseDir = Path("target/classes")
    ) {
      addFileset(AntFileSet(dir = Path("."), includes="LICENSE.txt"))
      addConfiguredManifest(new org.apache.tools.ant.taskdefs.Manifest() {
        setProject(AntProject())
        addConfiguredAttribute(new org.apache.tools.ant.taskdefs.Manifest.Attribute() {
          setName("SBuild-ComponentName")
          setValue("de.tototec.sbuild.ant")
        })
      })
    }.execute
  }

//   Target("phony:scaladoc") dependsOn compileCp exec { ctx: TargetContext =>
//     AntMkdir(dir = Path("target/scaladoc"))
//     IfNotUpToDate(Path("src/main/scala"), Path("target"), ctx) {
//       scala_tools_ant.AntScaladoc(
//         deprecation = "on",
//         unchecked = "on",
//         classpath = AntPath(ctx.prerequisites),
//         srcDir = AntPath("src/main/scala"),
//         destDir = Path("target/scaladoc")
//       )
//     }
//   }

}
