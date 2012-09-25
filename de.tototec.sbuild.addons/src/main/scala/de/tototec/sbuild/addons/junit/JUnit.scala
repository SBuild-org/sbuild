package de.tototec.sbuild.addons.junit

import de.tototec.sbuild.ExecutionFailedException
import java.net.URLClassLoader
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import de.tototec.sbuild.Project
import de.tototec.sbuild.LogLevel
import scala.collection.JavaConversions._

class JUnit(
  var classpath: Seq[File] = null,
  var classes: Seq[String] = null,
  val failOnError: Boolean = true)(implicit project: Project) {

  def execute {

    val cl = classpath match {
      case null => getClass.getClassLoader
      case Seq() => getClass.getClassLoader
      case cp => new URLClassLoader(cp.map { f => f.toURI().toURL() }.toArray, getClass.getClassLoader)
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