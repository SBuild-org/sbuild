package de.tototec.sbuild.addons.junit

import de.tototec.sbuild.ExecutionFailedException
import java.net.URLClassLoader
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import de.tototec.sbuild.Project
import de.tototec.sbuild.LogLevel
import scala.collection.JavaConversions._

/**
 * JUnit Addon, to run JUnit-based unit tests with SBuild.
 * 
 */
object JUnit {
  /**
   * Creates, configures and execute a JUnit test runner.
   * For parameter documentation refer to [[JUnit]].
   */
  def apply(
    classpath: Seq[File] = null,
    classes: Seq[String] = null,
    failOnError: Boolean = true)(implicit project: Project) = new JUnit(
    classpath = classpath,
    classes = classes,
    failOnError = failOnError
  ).execute
}

/**
 * JUnit Addon, to run JUnit-based unit tests with SBuild.
 *
 * @constructor
 * Creates a new JUnit addon instance.
 *
 * All constructor parameters can be omitted an set afterwards.
 * To execute all configured unit test, call [[JUnit#execute]].
 *
 * To easily create, configure and execute the JUnit test runner in one go, see [[JUnit$#apply]].
 *
 * @param classpath The classpath which contains the JUnit runner and the classes containing the test cases.
 * @param classes The fully qualified classes containing the JUnit test cases.
 * @param failOnError Control, whether failed tests should be handled as errors or not.
 *   When not, failed tests will be printed, but the build will continue.   
 *
 */
class JUnit(
  var classpath: Seq[File] = null,
  var classes: Seq[String] = null,
  val failOnError: Boolean = true)(implicit project: Project) {

  def execute {

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
                  classOf[JUnit].getClassLoader().loadClass(className);
              }
            }
          }
        }
    }

    val junitClass = try {
      cl.loadClass("org.junit.runner.JUnitCore")
    } catch {
      case e: ClassNotFoundException =>
        throw new ExecutionFailedException("org.junit.runner.JUnitCore was not found on the classpath.\nPlease add it to the 'classpath' attribute or the SBuild classapth.")
    }
    val junit = junitClass.newInstance

    val runMethod = junitClass.asInstanceOf[Class[_]].getMethod("run", classOf[Array[Class[_]]])
    val result = runMethod.invoke(junit, classes.map { c => cl.loadClass(c) }.toArray)

    val successMethod = result.getClass.asInstanceOf[Class[_]].getMethod("wasSuccessful")
    val success: Boolean = successMethod.invoke(result).asInstanceOf[Boolean]

    val runCountMethod = result.getClass.asInstanceOf[Class[_]].getMethod("getRunCount")
    val runCount: Int = runCountMethod.invoke(result).asInstanceOf[Int]

    val failureCountMethod = result.getClass.asInstanceOf[Class[_]].getMethod("getFailureCount")
    val failureCount: Int = failureCountMethod.invoke(result).asInstanceOf[Int]

    val ignoreCountMethod = result.getClass.asInstanceOf[Class[_]].getMethod("getIgnoreCount")
    val ignoreCount: Int = ignoreCountMethod.invoke(result).asInstanceOf[Int]

    project.log.log(LogLevel.Info, "Executed tests: " + runCount + ", Failures: " + failureCount + ", Ignored: " + ignoreCount)

    if (!success) {
      val failuresMethod = result.getClass.asInstanceOf[Class[_]].getMethod("getFailures")
      val failures: java.util.List[_] = failuresMethod.invoke(result).asInstanceOf[java.util.List[_]]

      project.log.log(LogLevel.Info, "Failures:\n- " + failures.mkString("\n- "))

      if (failOnError)
        throw new ExecutionFailedException("Some JUnit test failed.")
    }
  }

}