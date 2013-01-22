package de.tototec.sbuild.addons.scala

import java.io.File
import java.net.URLClassLoader
import de.tototec.sbuild.Project
import de.tototec.sbuild.LogLevel
import de.tototec.sbuild.ExecutionFailedException
import de.tototec.sbuild.Util
import de.tototec.sbuild.Path
import java.io.InputStream
import java.io.OutputStream
import de.tototec.sbuild.addons.support.ForkSupport

object Scalac {

  def apply(
    compilerClasspath: Seq[File] = null,
    classpath: Seq[File] = null,
    srcDir: File = null,
    srcDirs: Seq[File] = null,
    destDir: File = null,
    encoding: String = "UTF-8",
    unchecked: java.lang.Boolean = null,
    deprecation: java.lang.Boolean = null,
    verbose: java.lang.Boolean = null,
    target: String = null,
    debugInfo: String = null,
    fork: Boolean = false,
    additionalScalacArgs: Array[String] = null)(implicit project: Project) =
    new Scalac(
      compilerClasspath = compilerClasspath,
      classpath = classpath,
      srcDir = srcDir,
      srcDirs = srcDirs,
      destDir = destDir,
      encoding = encoding,
      unchecked = unchecked,
      deprecation = deprecation,
      verbose = verbose,
      target = target,
      debugInfo = debugInfo,
      fork = fork,
      additionalScalacArgs = additionalScalacArgs
    ).execute

}

class Scalac(
  var compilerClasspath: Seq[File] = null,
  var classpath: Seq[File] = null,
  var srcDir: File = null,
  var srcDirs: Seq[File] = null,
  var destDir: File = null,
  var encoding: String = "UTF-8",
  var unchecked: java.lang.Boolean = null,
  var deprecation: java.lang.Boolean = null,
  var verbose: java.lang.Boolean = null,
  var target: String = null,
  var debugInfo: String = null,
  var fork: Boolean = false,
  var additionalScalacArgs: Array[String] = null)(implicit project: Project) {

  val scalacClassName = "scala.tools.nsc.Main"

  def execute {
    require(compilerClasspath != null && !compilerClasspath.isEmpty, "No compiler classpath set.")

    var args = Array[String]()

    if (classpath != null) {
      val cPath = ForkSupport.pathAsArg(classpath)
      project.log.log(LogLevel.Debug, "Using classpath: " + cPath)
      args ++= Array("-classpath", cPath)
    }

    if (destDir != null) args ++= Array("-d", destDir.getAbsolutePath)

    if (encoding != null) args ++= Array("-encoding", encoding)
    if (unchecked != null && unchecked.booleanValue) args ++= Array("-unchecked")
    if (deprecation != null && deprecation.booleanValue) args ++= Array("-deprecation")
    if (verbose != null && verbose.booleanValue) args ++= Array("-verbose")
    if (target != null) args ++= Array("-target:" + target)
    if (debugInfo != null) {
      args ++= Array("-g:" + debugInfo)
    }

    if (additionalScalacArgs != null && !additionalScalacArgs.isEmpty) args ++= additionalScalacArgs

    var allSrcDirs = Seq[File]()
    if (srcDir != null) allSrcDirs ++= Seq(srcDir)
    if (srcDirs != null) allSrcDirs ++= srcDirs
    require(!allSrcDirs.isEmpty, "No source path(s) set.")

    val sourceFiles: Seq[File] = allSrcDirs.flatMap { dir =>
      project.log.log(LogLevel.Debug, "Search files in dir: " + dir)
      val files = Util.recursiveListFiles(dir, """.*\.(java|scala)$""".r)
      project.log.log(LogLevel.Debug, "Found files: " + files.mkString(", "))
      files
    }

    if (!sourceFiles.isEmpty) {
      val absSourceFiles = sourceFiles.map(f => f.getAbsolutePath)
      project.log.log(LogLevel.Debug, "Found source files: " + absSourceFiles.mkString(", "))
      args ++= absSourceFiles
    }

    project.log.log(LogLevel.Info, s"Compiling ${sourceFiles.size} source files to ${destDir}")
    
    if (fork) {
      compileExternal(args)
    } else {
      compileInternal(args)
    }

  }

  def compileExternal(args: Array[String]) {
    val command = Array("java", "-cp", ForkSupport.pathAsArg(compilerClasspath), scalacClassName) ++ args
    val result = ForkSupport.runAndWait(command)
    if (result != 0) {
      val e = new ExecutionFailedException("Compile Errors. See compiler output.")
      e.buildScript = Some(project.projectFile)
      throw e
    }
  }

  def compileInternal(args: Array[String]) {

    val compilerClassLoader = new URLClassLoader(compilerClasspath.map { f => f.toURI().toURL() }.toArray, classOf[Scalac].getClassLoader)
    project.log.log(LogLevel.Debug, "Using addional compiler classpath: " + compilerClassLoader.getURLs().mkString(", "))

    //    val arrayClass = compilerClassLoader.loadClass("[Ljava.lang.String;")
    val arrayInstance = java.lang.reflect.Array.newInstance(classOf[String], args.size)
    0.to(args.size - 1).foreach { i =>
      java.lang.reflect.Array.set(arrayInstance, i, args(i))
    }
    val arrayClass = arrayInstance.getClass

    val compilerClass = compilerClassLoader.loadClass(scalacClassName)
    //    project.log.log(LogLevel.Debug, "Methods: " + compilerClass.getMethods.mkString("\n  "))
    val compilerMethod = compilerClass.getMethod("process", Array(arrayClass): _*)
    project.log.log(LogLevel.Debug, "Executing Scala Compiler with args: " + args.mkString(" "))

    compilerMethod.invoke(null, arrayInstance)

    val reporterMethod = compilerClass.getMethod("reporter")
    val reporter = reporterMethod.invoke(null)
    val hasErrorsMethod = reporter.asInstanceOf[Object].getClass.getMethod("hasErrors")
    val hasErrors = hasErrorsMethod.invoke(reporter).asInstanceOf[Boolean]
    if (hasErrors) {
      val e = new ExecutionFailedException("Compile Errors. See compiler output.")
      e.buildScript = Some(project.projectFile)
      throw e

    }
  }

}