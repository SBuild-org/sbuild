package de.tototec.sbuild.addons.testng

import java.io.File
import java.net.URLClassLoader

import scala.Array.canBuildFrom
import scala.collection.JavaConverters.seqAsJavaListConverter

import de.tototec.sbuild.ExecutionFailedException
import de.tototec.sbuild.Logger
import de.tototec.sbuild.Project
import de.tototec.sbuild.addons.support.ForkSupport

class TestNg(
    var classpath: Seq[File] = null,
    var reportDir: File = null,
    var parallel: String = null,
    var threadCount: java.lang.Integer = null,
    var dataProviderThreadCount: java.lang.Integer = null,
    var groups: Seq[String] = null,
    var excludeGroups: Seq[String] = null,
    var listeners: Seq[String] = null,
    var testClasses: Seq[String] = null,
    var methods: Seq[String] = null,
    var methodSelectors: Seq[String] = null,
    var sourceDirs: Seq[File] = null,
    var testJar: File = null,
    var testName: String = null,
    var testNames: Seq[String] = null,
    var testRunFactory: String = null,
    var xmlPathInJar: String = null,
    var mixed: java.lang.Boolean = null,
    var useDefaultListeners: java.lang.Boolean = null,
    var suites: Seq[File] = null,
    var fork: Boolean = false)(implicit val _project: Project) {

  private[this] val log = Logger[TestNg]

  private[this] val testNgRunnerClass = "org.testng.TestNG"

  def execute {

    val useCommandlineConfig = false

    var runnerArgs: Array[String] = Array()

    if (fork || !useCommandlineConfig) {
      if (reportDir != null) runnerArgs ++= Array("-d", reportDir.getPath)
      if (parallel != null) runnerArgs ++= Array("-parallel", parallel)
      if (threadCount != null) runnerArgs ++= Array("-threadcount", threadCount.toString)
      if (dataProviderThreadCount != null) runnerArgs ++= Array("-dataproviderthreadcount", dataProviderThreadCount.toString)
      if (groups != null && !groups.isEmpty) runnerArgs ++= Array("-groups", groups.mkString(","))
      if (excludeGroups != null && !excludeGroups.isEmpty) runnerArgs ++= Array("-excludegroups", excludeGroups.mkString(","))
      if (listeners != null && !listeners.isEmpty) runnerArgs ++= Array("-listener", listeners.mkString(","))

      if (testClasses != null && !testClasses.isEmpty) runnerArgs ++= Array("-testclass", testClasses.mkString(","))
      if (methods != null && !methods.isEmpty) runnerArgs ++= Array("-methods", methods.mkString(","))
      if (methodSelectors != null && !methodSelectors.isEmpty) runnerArgs ++= Array("-methodselectors", methodSelectors.mkString(","))
      if (sourceDirs != null && !sourceDirs.isEmpty) runnerArgs ++= Array("-sourcedir", sourceDirs.map(_.getPath).mkString(";"))

      if (testJar != null) runnerArgs ++= Array("-testjar", testJar.getPath)
      if (testName != null) runnerArgs ++= Array("-testname", testName)
      if (testNames != null && !testNames.isEmpty) runnerArgs ++= Array("-testnames", testNames.mkString(","))
      if (testRunFactory != null) runnerArgs ++= Array("-testrunfactory", testRunFactory)

      if (xmlPathInJar != null) runnerArgs ++= Array("-xmlpathinjar", xmlPathInJar)

      if (mixed != null) runnerArgs ++= Array("-mixed")
      if (useDefaultListeners != null) runnerArgs ++= Array("-usedefaultlisteners")

      if (suites != null && !suites.isEmpty) runnerArgs ++= suites.map(_.getPath).toArray[String]
    }

    val retVal: Int = if (fork) {

      ForkSupport.runJavaAndWait(classpath, Array(testNgRunnerClass) ++ runnerArgs)

    } else {

      val cl = classpath match {
        case null => getClass.getClassLoader
        case Seq() => getClass.getClassLoader
        case cp =>
          new URLClassLoader(cp.map { f => f.toURI().toURL() }.toArray, null) {
            override protected def loadClass(className: String, resolve: Boolean): Class[_] = {
              synchronized {
                try {
                  super.loadClass(className, resolve)
                } catch {
                  case e: NoClassDefFoundError =>
                    classOf[TestNg].getClassLoader().loadClass(className);
                }
              }
            }
          }
      }

      log.debug("Running TestNG with\n  classpath: " + (cl match {
        case cp: URLClassLoader => cp.getURLs.mkString(", ")
        case x => "<SBuild classpath>"
      }))

      try {
        val runnerClass = cl.loadClass(testNgRunnerClass)

        if (useCommandlineConfig) {

          val runner = runnerClass.newInstance()

          val cmdlineArgsClass = cl.loadClass("org.testng.CommandLineArgs")
          val cmdlineArgs = cmdlineArgsClass.newInstance()
          def config(fieldName: String, value: Any) {
            val field = cmdlineArgsClass.getField(fieldName)
            if (!field.isAccessible()) {
              field.setAccessible(true)
            }
            field.set(cmdlineArgs, value)
          }

          if (reportDir != null) config("outputDirectory", reportDir)
          if (parallel != null) config("parallelMode", parallel)
          if (threadCount != null) config("threadCount", threadCount)
          if (dataProviderThreadCount != null) config("dataProviderThreadCount", dataProviderThreadCount)
          if (groups != null && !groups.isEmpty) config("groups", groups.mkString(","))
          if (excludeGroups != null && !excludeGroups.isEmpty) config("excludedGroups", excludeGroups.mkString(","))
          if (listeners != null && !listeners.isEmpty) config("listener", listeners.mkString(","))

          if (testClasses != null && !testClasses.isEmpty) config("testClass", testClasses.mkString(","))
          if (methods != null && !methods.isEmpty) { config("commandLineMethods", methods.asJava) }
          if (methodSelectors != null && !methodSelectors.isEmpty) config("-methodselectors", methodSelectors.mkString(","))
          if (sourceDirs != null && !sourceDirs.isEmpty) config("-sourcedir", sourceDirs.map(_.getPath).mkString(";"))

          if (testJar != null) config("testJar", testJar.getPath)
          if (testName != null) config("testName", testName)
          if (testNames != null && !testNames.isEmpty) config("testNames", testNames.mkString(","))
          if (testRunFactory != null) config("testRunnerFactory", testRunFactory)

          if (xmlPathInJar != null) config("xmlPathInJar", xmlPathInJar)

          if (mixed != null) config("mixed", true)
          if (useDefaultListeners != null) config("useDefaultListeners", true)

          if (suites != null && !suites.isEmpty) config("suiteFiles", suites.map(_.getPath).asJava)

          log.debug("TestNG commandline args object: " + cmdlineArgs)

          runnerClass.getMethod("configure", cmdlineArgsClass).invoke(runner, cmdlineArgs.asInstanceOf[cmdlineArgsClass.type])
          
          // TODO: finish and test

          1
        } else {

          // TODO: instead of privateMain use new TestNG + CommandLineArgs object
          val runMethod = runnerClass.asInstanceOf[Class[_]].getMethods().filter(m => m.getName == "privateMain").head
          val testNgResult = runMethod.invoke(null, runnerArgs, null /* test listener */ )

          val failed = testNgResult.asInstanceOf[{ def hasFailure(): Boolean }].hasFailure
          if (failed) 1 else 0
        }

      } catch {
        case e: ClassNotFoundException =>
          throw new ExecutionFailedException("Some TestNG classes could not be found on the classpath.\nPlease add it to the 'classpath' attribute or the SBuild classapth.", e)
      }

    }

    if (retVal != 0) {
      val e = new ExecutionFailedException("TestNG errors.")
      e.buildScript = Some(_project.projectFile)
      throw e
    }

  }

}