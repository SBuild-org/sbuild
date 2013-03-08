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

/**
 * Companion object for [[Scalac]], the Scala Compiler Addon.
 *
 * Use [[Scalac$#apply]] to configure and execute it in one go.
 *
 */
object Scalac {

  /**
   * Configure and execute the Scalac addon.
   *
   * For parameter documentation see the [[Scalac]] constructor.
   *
   */
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
    additionalScalacArgs: Seq[String] = null,
    // since 0.3.2.9002
    sources: Seq[File] = null)(implicit project: Project) =
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
      additionalScalacArgs = additionalScalacArgs,
      sources = sources
    ).execute

}

/**
 * The [[http://www.scala-lang.org/ Scala]] [[http://www.scala-lang.org/docu/files/tools/scalac.html Compiler]] addon.
 *
 * The compiler can be configured via constructor parameter or `var`s. To compile use [[Scalac#execute]].
 *
 * To easily configure and execute the compiler in one go, see [[Scalac$#apply]].
 *
 * '''Example:'''
 * {{{
 * import de.tototec.sbuild._
 * import de.tototec.sbuild.TargetRef._
 * 
 * @version("0.4.0")
 * class SBuild(implicit _project: Project) {
 *
 *   // compile classpath
 *   val compileCp = ...
 *
 *   // The Scalac compiler classpath, here version 2.10.0
 *   val scalaVersion = "2.10.0"
 *   val scalaCompilerCp =
 *   s"mvn:org.scala-lang:scala-library:${scalaVersion}" ~
 *   s"mvn:org.scala-lang:scala-compiler:${scalaVersion}" ~
 *   s"mvn:org.scala-lang:scala-reflect:${scalaVersion}"
 *
 *   Target("phony:compile").cacheable dependsOn scalaCompilerCp ~ compileCp ~ "scan:src/main/scala" exec {
 *     val sources = Path("src/main/scala")
 *     val target = Path("target/classes")
 *     addons.scala.Scalac(
 *       compilerClasspath = scalaCompilerCp.files
 *       classpath = compileCp.files,
 *       sources = "scan:src/main/scala".files,
 *       destDir = target,
 *       target = "jvm-1.6",
 *       deprecation = true,
 *       unchecked = true,
 *       debugInfo = "vars",
 *       fork = true
 *     )
 *   }
 * }
 * }}}
 *
 * @since 0.3.0
 * 
 * @constructor
 * Create a new Scalac Compiler addon instance. All parameters can be omitted and set later.
 *
 * The source files can be given via multiple parameters, '''sources''', '''srcDir''' and '''srcDirs''', and will be joined.
 *
 * The Scala compiler is able to read Java source files in order to resolve dependencies.
 * It will not create class files for read Java files, though.
 * All Java files given or found on the source directories will be read.
 *
 *
 * @param compilerClasspath The classpath which contains the compiler and its dependencies. (E.g. scala-compiler.jar, scala-reflect.jar, ...)
 *   If not specified, the compiler must be in the SBuild classpath, e.g. by adding it with `@classpath` annotation. 
 *   If `fork` option is also set to `true`, this parameter is required.
 * @param classpath The classpath is forwarded to the compiler and used to load dependencies of the sources. 
 *   It must also contain the scala library.
 * @param srcDir A directory containing Scala (and Java) source files. 
 *   If multiple directories are needed, `srcDirs` parameter can be used.
 * @param srcDirs Multiple directories containing Scala and Java source files.
 *   If only one directory is needed, `srcDir` parameter can be used for convenience.
 * @param sources Source files to be compiled. ''Since 0.4.0''
 * @param destDir The directory, where the compiled class files will be stored. 
 *   If the directory does not exists, it will be created.
 * @param encoding The encoding of the source files.
 * @param unchecked If `true`, the compiler enables detailed unchecked (erasure) warnings.
 * @param deprecation If `true` , the compiler emit warning and location for usages of deprecated APIs.
 * @param verbose If `true`, the compiler outputs messages about what it is doing.
 * @param target Target platform for object files.
 *   The supported values depend on the used version of the Scalac compiler. 
 *   Scalac up to 2.9.x supports the following values: jvm-1.5 (default), msil.
 *   Scalac 2.10.x also supports jvm-1.6 (default), jvm-1.7 and various others.
 *   Please consult the Scalac documentation.
 * @param debugInfo The level of generated debugging info. 
 *   Supported values: none, source, line, vars (default), notailcalls.
 *   "none" generates no debugging info, 
 *   "source" generates only the source file attribute, 
 *   "line" generates source and line number information, 
 *   "vars" generates source, line number and local variable information, 
 *   "notc" generates all of the above and will not perform tail call optimization.
 * @param fork If `true` runs the compiler in a separate process. 
 *   If not set or set to `false`, the Scala version of SBuild and the used Scala compiler must be binary compatible.
 *   If you indent to use a different (binary incompatible) Scala version to the one SBuild runs with, you should set it to `true`.
 *   If `true`, also the `compilerClasspath` parameter must be specified.
 * @param additionalScalacArgs Additional arguments directly passed to the Scala compiler. 
 *   Refer to the scalac manual or inspect `scalac -help` output.
 *
 */
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
  var additionalScalacArgs: Seq[String] = null,
  // since 0.3.2.9002
  var sources: Seq[File] = null)(implicit project: Project) {

  val scalacClassName = "scala.tools.nsc.Main"

  override def toString(): String = getClass.getSimpleName +
    "(compilerClasspath=" + compilerClasspath +
    ",classpath=" + classpath +
    ",sources=" + sources +
    ",srcDir=" + srcDir +
    ",srdDirs=" + srcDirs +
    ",destDir=" + destDir +
    ",encoding=" + encoding +
    ",unchecked=" + unchecked +
    ",deprecation=" + deprecation +
    ",verbose=" + verbose +
    ",target=" + target +
    ",debugInfo=" + debugInfo +
    ",fork=" + fork +
    ",additionalScalacArgs=" + additionalScalacArgs +
    ")"

  /**
   * Execute the Scala compiler.
   */
  def execute {
    project.log.log(LogLevel.Debug, "About to execute " + this)

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
    require(!allSrcDirs.isEmpty || !sources.isEmpty, "No source path(s) and no sources set.")

    val sourceFiles: Seq[File] =
      (if (sources == null) Seq() else sources) ++
        allSrcDirs.flatMap { dir =>
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

    if (destDir != null && !sourceFiles.isEmpty) destDir.mkdirs

    val result =
      if (fork) compileExternal(args)
      else compileInternal(args)

    if (result != 0) {
      val ex = new ExecutionFailedException("Compile Errors. See compiler output.")
      ex.buildScript = Some(project.projectFile)
      throw ex
    }

  }

  protected def compileExternal(args: Array[String]) =
    ForkSupport.runJavaAndWait(compilerClasspath, Array(scalacClassName) ++ args)

  protected def compileInternal(args: Array[String]): Int = {

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
    if (hasErrors) 1 else 0
  }

}