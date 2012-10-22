import de.tototec.sbuild._
import de.tototec.sbuild.TargetRefs._
import de.tototec.sbuild.ant._
import de.tototec.sbuild.ant.tasks._

@version("0.1.3")
@classpath(
  "http://repo1.maven.org/maven2/org/apache/ant/ant/1.8.3/ant-1.8.3.jar",
  "http://repo1.maven.org/maven2/org/scala-lang/scala-compiler/2.9.2/scala-compiler-2.9.2.jar",
  "http://dl.dropbox.com/u/2590603/bnd/biz.aQute.bnd.jar"
)
class SBuild(implicit project: Project) {

  SchemeHandler("http", new HttpSchemeHandler())
  SchemeHandler("mvn", new MvnSchemeHandler())
  SchemeHandler("zip", new ZipSchemeHandler())

  val version = Prop("SBUILD_ECLIPSE_VERSION", "0.1.3.9000")
  val sbuildVersion = Prop("SBUILD_VERSION", version)
  val eclipseJar = "target/de.tototec.sbuild.eclipse.plugin-" + version + ".jar"

  val scalaVersion = "2.9.2"

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
      "zip:file=swt-debug.jar;archive=http://archive.eclipse.org/eclipse/downloads/drops/R-3.3-200706251500/swt-3.3-gtk-linux-x86_64.zip" ~
      "http://cmdoption.tototec.de/cmdoption/attachments/download/3/de.tototec.cmdoption-0.1.0.jar"

  ExportDependencies("eclipse.classpath", compileCp)

  Target("phony:all") dependsOn "clean" ~ eclipseJar

  Target("phony:clean") exec {
    AntDelete(dir = Path("target"))
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
Bundle-Activator: de.tototec.sbuild.eclipse.plugin.internal.SBuildClasspathActivator
Bundle-ActivationPolicy: lazy
Implementation-Version: ${Bundle-Version}
Private-Package: \
 de.tototec.sbuild.eclipse.plugin, \
 de.tototec.sbuild.eclipse.plugin.internal
Import-Package: \
 !de.tototec.sbuild.*, \
 !de.tototec.cmdoption.*, \
 org.eclipse.core.runtime;registry=!;common=!;version="3.3.0", \
 org.eclipse.core.internal.resources, \
 *
DynamicImport-Package: \
 !scala.tools.*, \
 scala.*
Include-Resource: """ + Path("src/main/resources") + """,""" + Path("target/bnd-resources") + """
-removeheaders: Include-Resource
Bundle-RequiredExecutionEnvironment: J2SE-1.5
"""
    AntEcho(message = bnd, file = ctx.targetFile.get)
  }

  Target(eclipseJar) dependsOn (compileCp ~ "compile" ~ "target/bnd.bnd") exec { ctx: TargetContext =>
    //     val jarTask = new AntJar(destFile = ctx.targetFile.get, baseDir = Path("target/classes"))
    //     jarTask.addFileset(AntFileSet(dir = Path("."), includes = "LICENSE.txt"))
    //     jarTask.execute

    AntDelete(dir = Path("target/bnd-classes"))
    AntCopy(toDir = Path("target/bnd-classes"),
      fileSets = Seq(AntFileSet(dir = Path("target/classes"), excludes = "**/SBuildClasspathProjectReaderImpl**.class")))

    AntDelete(dir = Path("target/bnd-resources/OSGI-INF/projectReaderLib"))
    AntMkdir(dir = Path("target/bnd-resources/OSGI-INF/projectReaderLib"))
    AntCopy(toDir = Path("target/bnd-resources/OSGI-INF/projectReaderLib"),
      fileSets = Seq(AntFileSet(dir = Path("target/classes"), includes = "**/SBuildClasspathProjectReaderImpl**.class")))

    aQute_bnd_ant.AntBnd(
      classpath = "target/bnd-classes," + ctx.fileDependencies.filter(_.getName.endsWith(".jar")).mkString(","),
      eclipse = false,
      failOk = false,
      exceptions = true,
      files = ctx.fileDependencies.filter(_.getName.endsWith(".bnd")).mkString(","),
      output = ctx.targetFile.get
    )
  }

}
