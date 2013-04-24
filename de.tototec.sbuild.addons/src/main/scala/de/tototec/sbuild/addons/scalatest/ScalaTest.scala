package de.tototec.sbuild.addons.scalatest

import de.tototec.sbuild.Project
import java.io.File
import java.net.URLClassLoader
import de.tototec.sbuild.ExecutionFailedException
import de.tototec.sbuild.LogLevel
import de.tototec.sbuild.addons.support.ForkSupport

/**
 * Companion object for [[ScalaTest]], the ScalaTest Compiler Addon.
 *
 * Use [[ScalaTest$#apply]] to configure and execute it in one go.
 *
 */
object ScalaTest {

  /**
   * Configure and execute the ScalaTest addon.
   *
   * For parameter documentation see the [[ScalaTest]] constructor.
   *
   */
  def apply(
    classpath: Seq[File] = null,
    runPath: Seq[String] = null,
    reporter: String = null,
    configMap: Map[String, String] = null,
    includes: Seq[String] = null,
    excludes: Seq[String] = null,
    // since 0.1.5.9000
    parallel: java.lang.Boolean = null,
    threadCount: java.lang.Integer = null,
    suites: Seq[String] = null,
    packages: Seq[String] = null,
    packagesRecursive: Seq[String] = null,
    testNgTests: Seq[String] = null,
    junitTests: Seq[String] = null,
    // since 0.2.0.9000
    fork: Boolean = false)(implicit project: Project) =
    new ScalaTest(
      classpath = classpath,
      runPath = runPath,
      reporter = reporter,
      configMap = configMap,
      includes = includes,
      excludes = excludes,
      parallel = parallel,
      threadCount = threadCount,
      suites = suites,
      packages = packages,
      packagesRecursive = packagesRecursive,
      testNgTests = testNgTests,
      junitTests = junitTests,
      fork = fork
    ).execute
}

/**
 * ScalaTest Addon to run unit tests with [[http://www.scalatest.org/ ScalaTest]].
 *
 * The ScalaTest runner can be configured via constructor parameter or `var`s. To run use [[ScalaTest#execute]].
 *
 * To easily configure and execute the test runner in one go, see [[ScalaTest$#apply]].
 *
 * @since 0.1.1
 *
 * @constructor
 * Create a new ScalaTest Runner addon instance. All parameters can be omitted and set later.
 *
 *
 * @param classpath The classpath used to run the ScalaTest itself.
 *   Also the test classes may be made available on the classpath,
 *   in which case no `runpath` needs to be specified.
 * @param runPath A list of filenames, directory paths,
 *   and/or URLs that Runner uses to load classes for the running test.
 *   If runpath is specified, Runner creates a custom class loader to load classes available on the runpath.
 *   The graphical user interface reloads the test classes anew for each run by creating
 *   and using a new instance of the custom class loader for each run.
 *   The classes that comprise the test may also be made available on the classpath,
 *   in which case no runpath need be specified.
 * @param reporter
 * @param configMap
 * @param includes
 * @param excludes
 * @param parallel ''Since 0.1.5.9000.''
 * @param threadCount ''Since 0.1.5.9000.''
 * @param suites ''Since 0.1.5.9000.''
 * @param packages ''Since 0.1.5.9000.''
 * @param packagesRecursive ''Since 0.1.5.9000.''
 * @param testNgTests ''Since 0.1.5.9000.''
 * @param junitTests ''Since 0.1.5.9000.''
 * @param fork ''Since 0.2.0.9000.''
 *
 */
class ScalaTest(
  var classpath: Seq[File] = null,
  var runPath: Seq[String] = null,
  var reporter: String = null,
  var configMap: Map[String, String] = null,
  var includes: Seq[String] = null,
  var excludes: Seq[String] = null,
  // since 0.1.5.9000
  var parallel: java.lang.Boolean = null,
  var threadCount: java.lang.Integer = null,
  var suites: Seq[String] = null,
  var packages: Seq[String] = null,
  var packagesRecursive: Seq[String] = null,
  var testNgTests: Seq[String] = null,
  var junitTests: Seq[String] = null,
  // since 0.2.0.9000
  var fork: Boolean = false)(implicit project: Project) {

  val scalaTestClassName = "org.scalatest.tools.Runner"

  /** Execute this ScalaTest runner. */
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
    if (includes != null && !includes.isEmpty) args ++= Array("-n", whiteSpaceSeparated(includes))
    if (excludes != null && !excludes.isEmpty) args ++= Array("-n", whiteSpaceSeparated(excludes))
    if (parallel != null && parallel.booleanValue) {
      if (threadCount != null) args ++= Array("-c" + threadCount.intValue)
      else args ++= Array("-c")
    }
    if (suites != null && !suites.isEmpty) args ++= Array("-s", whiteSpaceSeparated(suites))
    if (packages != null && !packages.isEmpty) args ++= Array("-m", whiteSpaceSeparated(packages))
    if (packagesRecursive != null && !packagesRecursive.isEmpty) args ++= Array("-w", whiteSpaceSeparated(packagesRecursive))
    if (testNgTests != null && !testNgTests.isEmpty) args ++= Array("-t", whiteSpaceSeparated(testNgTests))
    if (junitTests != null && !junitTests.isEmpty) args ++= Array("-j", whiteSpaceSeparated(junitTests))

    project.log.log(LogLevel.Info, "Running ScalaTest...")

    val retVal = if (fork) {
      ForkSupport.runJavaAndWait(classpath, Array(scalaTestClassName) ++ args)

    } else {

      val cl = classpath match {
        case null => getClass.getClassLoader
        case Seq() => getClass.getClassLoader
        case cp =>
          // TODO: make inclusion of parent classloader an option (e.g. includeSBuildRuntime)
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
        cl.loadClass(scalaTestClassName)
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
        case x if x.isInstanceOf[Boolean] && x == true => 0 // success
        case _ => 1
      }

    }

    if (retVal != 0) {
      val e = new ExecutionFailedException("ScalaTest errors.")
      e.buildScript = Some(project.projectFile)
      throw e
    }

  }

}

