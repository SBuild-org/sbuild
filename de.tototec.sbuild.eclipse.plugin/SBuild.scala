import de.tototec.sbuild._
import de.tototec.sbuild.TargetRefs._
import de.tototec.sbuild.ant._
import de.tototec.sbuild.ant.tasks._

@version("0.1.0")
@classpath(
  "http://repo1.maven.org/maven2/org/apache/ant/ant/1.8.3/ant-1.8.3.jar",
  "http://repo1.maven.org/maven2/org/scala-lang/scala-compiler/2.9.2/scala-compiler-2.9.2.jar",
  "http://dl.dropbox.com/u/2590603/bnd/biz.aQute.bnd.jar"
)
class SBuild(implicit project: Project) {

  SchemeHandler("http", new HttpSchemeHandler(Path(".sbuild/http")))
  SchemeHandler("mvn", new MvnSchemeHandler(Path(Prop("mvn.repo", ".sbuild/mvn"))))

  val version = Prop("SBUILD_ECLIPSE_VERSION", "0.1.0.SVN")
  val sbuildVersion = Prop("SBUILD_VERSION", version)
  val eclipseJar = "target/de.tototec.sbuild.eclipse.plugin-" + version + ".jar"

  val scalaVersion = "2.9.2"

  val swtJar = "target/libs/swt-debug.jar"

  val compileCp =
    ("mvn:org.scala-lang:scala-library:" + scalaVersion) ~
      ("../de.tototec.sbuild/target/de.tototec.sbuild.jar") ~
      "mvn:org.osgi:org.osgi.core:4.2.0" ~
      "mvn:org.eclipse.core:runtime:3.3.100-v20070530" ~
      "mvn:org.eclipse.core:resources:3.3.0-v20070604" ~
      "mvn:org.eclipse.core:jobs:3.3.0-v20070423" ~
      "mvn:org.eclipse.equinox:common:3.3.0-v20070426" ~
      "mvn:org.eclipse.core:contenttype:3.2.100-v20070319" ~
      "mvn:org.eclipse:jface:3.3.0-I20070606-0010" ~
      "mvn:org.eclipse:swt:3.3.0-v3346" ~
      "mvn:org.eclipse.jdt:core:3.3.0-v_771" ~
      "mvn:org.eclipse.jdt:ui:3.3.0-v20070607-0010" ~
      "mvn:org.eclipse.core:commands:3.3.0-I20070605-0010" ~
      "mvn:org.eclipse.equinox:registry:3.3.0-v20070522" ~
      "mvn:org.eclipse.equinox:preferences:3.2.100-v20070522" ~
      swtJar ~
      "http://cmdoption.tototec.de/cmdoption/attachments/download/3/de.tototec.cmdoption-0.1.0.jar"

  SetProp("eclipse.classpath",
    compileCp.targetRefs.
      map(t => "<dep><![CDATA[" +
        (if (t.explicitProject.isDefined) (t.explicitProject + "::") else "") + t.name + "]]></dep>").
      mkString("<deps>", "", "</deps>")
  )

  Target("phony:all") dependsOn "clean" ~ eclipseJar

  Target("phony:clean") exec {
    AntDelete(dir = Path("target"))
  }

  Target(swtJar) dependsOn "http://archive.eclipse.org/eclipse/downloads/drops/R-3.3-200706251500/swt-3.3-gtk-linux-x86_64.zip" exec { ctx: TargetContext =>
    new AntExpand(src = ctx.fileDependencies.head, dest = Path("target/libs")) {
      addPatternset(
        new org.apache.tools.ant.types.PatternSet() {
          setIncludes("swt-debug.jar")
        })
    }.execute
  }

  Target("phony:compile") dependsOn (compileCp) exec { ctx: TargetContext =>
    val input = "src/main/scala"
    val output = "target/classes"
    AntMkdir(dir = Path(output))
    IfNotUpToDate(srcDir = Path(input), stateDir = Path("target"), ctx = ctx) {
      scala_tools_ant.AntScalac(
        target = "jvm-1.5",
        encoding = "UTF-8",
        deprecation = "on",
        unchecked = "on",
        debugInfo = "vars",
        // this is necessary, because the scala ant tasks outsmarts itself 
        // when more than one scala class is defined in the same .scala file
        force = true,
        srcDir = AntPath(input),
        destDir = Path(output),
        classpath = AntPath(locations = ctx.fileDependencies)
      )
    }
  }

  Target("target/bnd.bnd") dependsOn project.projectFile exec { ctx: TargetContext =>
    val bnd = """
Bundle-SymbolicName: de.tototec.sbuild.eclipse.plugin;singleton:=true
Bundle-Version: """ + version + """
Implementation-Version: ${Bundle-Version}
Private-Package: \
 de.tototec.sbuild.eclipse.plugin, \
 de.tototec.sbuild.eclipse.plugin.internal, \
 de.tototec.sbuild, \
 de.tototec.sbuild.runner, \
 de.tototec.cmdoption, \
 de.tototec.cmdoption.handler
Import-Package: \
 org.eclipse.core.runtime;registry=!;common=!;version="3.3.0", \
 *
DynamicImport-Package: \
 !scala.tools.*, \
 scala.*
Include-Resource: """ + Path("src/main/resources") + """
-removeheaders: Include-Resource
Bundle-RequiredExecutionEnvironment: J2SE-1.5
"""
    AntEcho(message = bnd, file = ctx.targetFile.get)
  }

  Target(eclipseJar) dependsOn (compileCp ~ "compile" ~ "target/bnd.bnd") exec { ctx: TargetContext =>
    //     val jarTask = new AntJar(destFile = ctx.targetFile.get, baseDir = Path("target/classes"))
    //     jarTask.addFileset(AntFileSet(dir = Path("."), includes = "LICENSE.txt"))
    //     jarTask.execute

    aQute_bnd_ant.AntBnd(
      classpath = "target/classes," + ctx.fileDependencies.filter(_.getName.endsWith(".jar")).mkString(","),
      eclipse = false,
      failOk = false,
      exceptions = true,
      files = ctx.fileDependencies.filter(_.getName.endsWith(".bnd")).mkString(","),
      output = ctx.targetFile.get
    )
  }

}
