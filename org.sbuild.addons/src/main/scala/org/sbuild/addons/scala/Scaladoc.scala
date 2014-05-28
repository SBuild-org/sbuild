package org.sbuild.addons.scala

import java.io.File
import java.net.URLClassLoader

import scala.Array.canBuildFrom

import org.sbuild.CmdlineMonitor
import org.sbuild.ExecutionFailedException
import org.sbuild.Logger
import org.sbuild.Project
import org.sbuild.ProjectConfigurationException
import org.sbuild.RichFile
import org.sbuild.addons.support.ForkSupport

object Scaladoc {

  /**
   * Run the scaladoc generator.
   *
   * @since 0.4.0
   *
   * @param scaladocClasspath  Classpath used to load the scaladoc generator.
   * @param sources Sources, to generate the scaladoc for.
   */
  def apply(
    scaladocClasspath: Seq[File] = null,
    sources: Seq[File] = null,
    srcDir: File = null,
    srcDirs: Seq[File] = null,
    destDir: File = null,
    classpath: Seq[File] = null,
    encoding: String = null,
    unchecked: java.lang.Boolean = null,
    deprecation: java.lang.Boolean = null,
    verbose: java.lang.Boolean = null,
    implicits: java.lang.Boolean = null,
    docTitle: String = null,
    windowTitle: String = null,
    docVersion: String = null,
    docFooter: String = null,
    rawOutput: java.lang.Boolean = null,
    styleSheet: File = null,
    sourceUrl: String = null,
    fork: Boolean = false,
    additionalScaladocArgs: Seq[String] = null)(implicit project: Project) =
    new Scaladoc(
      scaladocClasspath = scaladocClasspath,
      sources = sources,
      srcDir = srcDir,
      srcDirs = srcDirs,
      destDir = destDir,
      classpath = classpath,
      encoding = encoding,
      unchecked = unchecked,
      deprecation = deprecation,
      verbose = verbose,
      implicits = implicits,
      docTitle = docTitle,
      windowTitle = windowTitle,
      docVersion = docVersion,
      docFooter = docFooter,
      rawOutput = rawOutput,
      styleSheet = styleSheet,
      sourceUrl = sourceUrl,
      fork = fork,
      additionalScaladocArgs = additionalScaladocArgs
    ).execute

}

/**
 * Generate Scaladoc.
 *
 * @since 0.4.0
 *
 * @param scaladocClasspath  Classpath used to load the scaladoc generator.
 * @param sources Sources, to generate the scaladoc for.
 *
 */
class Scaladoc(
    var scaladocClasspath: Seq[File] = null,
    var sources: Seq[File] = null,
    var srcDir: File = null,
    var srcDirs: Seq[File] = null,
    var destDir: File = null,
    var classpath: Seq[File] = null,
    var encoding: String = null,
    var unchecked: java.lang.Boolean = null,
    var deprecation: java.lang.Boolean = null,
    var verbose: java.lang.Boolean = null,
    var implicits: java.lang.Boolean = null,
    var docTitle: String = null,
    var windowTitle: String = null,
    var docVersion: String = null,
    var docFooter: String = null,
    var rawOutput: java.lang.Boolean = null,
    var styleSheet: File = null,
    var sourceUrl: String = null,
    var fork: Boolean = false,
    var additionalScaladocArgs: Seq[String] = null)(implicit project: Project) {

  private[this] val log = Logger[Scaladoc]
  private[this] def monitor = project.monitor

  val scaladocClassName = "scala.tools.nsc.ScalaDoc"

  override def toString(): String = getClass.getName +
    "(scaladocClasspath=" + scaladocClasspath +
    ",sources=" + sources +
    ",srcDir=" + srcDir +
    ",srcDirs=" + srcDirs +
    ",destDir=" + destDir +
    ",classpath=" + classpath +
    ",encoding=" + encoding +
    ",unchecked=" + unchecked +
    ",deprecation=" + deprecation +
    ",verbose=" + verbose +
    ",implicits=" + implicits +
    ",docTitle=" + docTitle +
    ",windowTitle=" + windowTitle +
    ",docVersion=" + docVersion +
    ",docFooter=" + docFooter +
    ",rawOutput=" + rawOutput +
    ",styleSheet=" + styleSheet +
    ",sourceUrl=" + sourceUrl +
    ",fork=" + fork +
    ",additionalScaladocArgs=" + additionalScaladocArgs +
    ")"

  def execute {
    log.debug("About to execute " + this)

    require(scaladocClasspath != null && !scaladocClasspath.isEmpty, "No scaladoc classpath set.")

    var args = Array[String]()

    if (classpath != null) {
      val cPath = ForkSupport.pathAsArg(classpath)
      log.debug("Using classpath: " + cPath)
      args ++= Array("-classpath", cPath)
    }

    if (destDir != null) args ++= Array("-d", destDir.getAbsolutePath)

    if (encoding != null) args ++= Array("-encoding", encoding)
    if (unchecked != null && unchecked.booleanValue) args ++= Array("-unchecked")
    if (deprecation != null && deprecation.booleanValue) args ++= Array("-deprecation")
    if (verbose != null && verbose.booleanValue) args ++= Array("-verbose")

    if (implicits != null && implicits.booleanValue) args ++= Array("-implicits")
    if (docTitle != null) args ++= Array("-doc-title", docTitle)
    if (windowTitle != null) args ++= Array("-windowtitle", windowTitle)
    if (docVersion != null) args ++= Array("-doc-version", docVersion)
    if (docFooter != null) args ++= Array("-doc-footer", docFooter)
    if (rawOutput != null && rawOutput.booleanValue) args ++= Array("-raw-output")
    if (styleSheet != null) {
      // TODO: check existence
      args ++= Array("-doc-stylesheet", styleSheet.getAbsolutePath)
    }
    if (sourceUrl != null) args ++= Array("-source-url", sourceUrl)

    if (additionalScaladocArgs != null && !additionalScaladocArgs.isEmpty) args ++= additionalScaladocArgs

    var allSrcDirs = Seq[File]()
    if (srcDir != null) allSrcDirs ++= Seq(srcDir)
    if (srcDirs != null) allSrcDirs ++= srcDirs
    require(!allSrcDirs.isEmpty || !sources.isEmpty, "No source path(s) and no sources set.")

    val sourceFiles: Seq[File] =
      (if (sources == null) Seq() else sources) ++
        allSrcDirs.flatMap { dir =>
          log.debug("Search files in dir: " + dir)
          val files = RichFile.listFilesRecursive(dir, """.*\.(java|scala)$""".r)
          log.debug("Found files: " + files.mkString(", "))
          files
        }

    if (!sourceFiles.isEmpty) {
      val absSourceFiles = sourceFiles.map(f => f.getAbsolutePath)
      log.debug("Found source files: " + absSourceFiles.mkString(", "))
      args ++= absSourceFiles
    } else {
      val ex = new ProjectConfigurationException("No source files given.")
      ex.buildScript = Some(project.projectFile)
      throw ex
    }

    monitor.info(CmdlineMonitor.Default, s"Generating Scaladoc for ${sourceFiles.size} source files to ${destDir}")

    if (destDir != null && !sourceFiles.isEmpty) destDir.mkdirs

    val result =
      if (fork) generateExternal(args)
      else generateInternal(args)

    if (result != 0) {
      val ex = new ExecutionFailedException("Scaladoc Errors. See scaladoc generator output.")
      ex.buildScript = Some(project.projectFile)
      throw ex
    }

  }

  def generateExternal(args: Array[String]) =
    ForkSupport.runJavaAndWait(scaladocClasspath, Array(scaladocClassName) ++ args)

  def generateInternal(args: Array[String]): Int = {
    val scaladocClassLoader = new URLClassLoader(scaladocClasspath.map { f => f.toURI().toURL() }.toArray, classOf[Scalac].getClassLoader)
    log.debug("Using additional scaladoc classpath: " + scaladocClassLoader.getURLs().mkString(", "))

    val scaladocClass = scaladocClassLoader.loadClass(scaladocClassName)
    val scaladocObjectClass = scaladocClassLoader.loadClass(scaladocClassName + "$")
    val scaladocObjectInstance = scaladocObjectClass.getField("MODULE$").get(scaladocClass)

    val processMethod = scaladocClass.getMethod("process", Array(args.getClass): _*)
    log.debug("Executing Scaladoc with args: " + args.mkString(" "))

    val success = processMethod.invoke(scaladocObjectInstance, args).asInstanceOf[Boolean]
    if (success) 0 else 1
  }

}