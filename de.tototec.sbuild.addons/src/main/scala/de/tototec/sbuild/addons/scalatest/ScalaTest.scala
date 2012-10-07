package de.tototec.sbuild.addons.scalatest

import de.tototec.sbuild.Project
import java.io.File
import java.net.URLClassLoader
import de.tototec.sbuild.ExecutionFailedException
import de.tototec.sbuild.LogLevel

object ScalaTest {
  def apply(
    classpath: Seq[File] = null,
    runPath: Seq[String] = null,
    reporter: String = null,
    configMap: Map[String, String] = null,
    includes: Seq[String] = null,
    excludes: Seq[String] = null)(implicit project: Project) =
    new ScalaTest(
      classpath = classpath,
      runPath = runPath,
      reporter = reporter,
      configMap = configMap,
      includes = includes,
      excludes = excludes
    ).execute

}

class ScalaTest(
  var classpath: Seq[File] = null,
  var runPath: Seq[String] = null,
  var reporter: String = null,
  var configMap: Map[String, String] = null,
  var includes: Seq[String] = null,
  var excludes: Seq[String] = null)(implicit project: Project) {

  def execute {

    def whiteSpaceSeparated(seq: Seq[String]): String = seq.map(_.replaceAll(" ", "\\ ")).mkString(" ")

    // As the runPath is seq of string, and we execute ScalaTest in the current VM, it is not
    // guaranteed that we run in the project directory, so relative runPathes must be converted
    // to absolute pathes
    lazy val absoluteRunPath = runPath.map { path =>
      path match {
        case x if x.startsWith("http:") => x
        case x if x.startsWith("https:") => x
        case x if x.startsWith("file:") => x
        case x => new File(x) match {
          case f if f.isAbsolute => x
          case f => new File(project.projectDirectory, x).getPath
        }
      }
    }

    var args = Array[String]()
    if (runPath != null) args ++= Array("-p", whiteSpaceSeparated(absoluteRunPath))
    if (reporter != null) args ++= Array("-" + reporter)
    if (configMap != null) configMap foreach {
      case (key, value) => args ++= Array("-D" + key + "=" + value)
    }
    if (includes != null) args ++= Array("-n", whiteSpaceSeparated(includes))
    if (excludes != null) args ++= Array("-n", whiteSpaceSeparated(excludes))

    val cl = classpath match {
      case null => getClass.getClassLoader
      case Seq() => getClass.getClassLoader
      case cp => 
        // Use a classloader that only loads from parent classloader if the given URLs do not contain the requested class.
        new URLClassLoader(cp.map { f => f.toURI().toURL() }.toArray, null) {
        override protected def loadClass(className: String, resolve: Boolean): Class[_] = {
          synchronized {
            try {
              super.loadClass(className, resolve)
            } catch {
              case e: NoClassDefFoundError =>
                classOf[ScalaTest].getClassLoader().loadClass(className);
            }
          }
        }
      }
    }

    //    Runner.run(args)

    val runnerClass = try {
      cl.loadClass("org.scalatest.tools.Runner")
    } catch {
      case e: ClassNotFoundException =>
        throw new ExecutionFailedException("org.scalatest.tools.Runner was not found on the classpath.\nPlease add it to the 'classpath' attribute or the SBuild classapth.")
    }

    project.log.log(LogLevel.Debug, "Running ScalaTest with\n  classpath: " + (cl match {
      case cp: URLClassLoader => cp.getURLs.mkString(", ")
      case x => "SBuild classpath"
    }) + "\n  args: " + args.mkString(", "))

    val runMethod = runnerClass.asInstanceOf[Class[_]].getMethod("run", classOf[Array[String]])
    runMethod.invoke(null, args) match {
      case x if x.isInstanceOf[Boolean] && x == true => // success
      case _ =>
        throw new ExecutionFailedException("Some ScalaTest tests failed.")
    }

  }

}
