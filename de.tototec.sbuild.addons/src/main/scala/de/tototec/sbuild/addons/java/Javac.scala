package de.tototec.sbuild.addons.java

import java.io.File
import java.lang.reflect.Method
import java.net.URLClassLoader

import de.tototec.sbuild.CmdlineMonitor
import de.tototec.sbuild.ExecutionFailedException
import de.tototec.sbuild.Logger
import de.tototec.sbuild.Path
import de.tototec.sbuild.Project
import de.tototec.sbuild.Util
import de.tototec.sbuild.addons.support.ForkSupport

/**
 * Java Compiler Addon.
 *
 * Use [[de.tototec.sbuild.addons.java.Javac$#apply]] to configure and execute it in one go.
 *
 */
object Javac {

  /**
   * Creates, configures and executes the Javac Addon.
   *
   * For parameter documentation see the [[Javac]] constructor.
   *
   * @since 0.4.0
   */
  def apply(compilerClasspath: Seq[File] = null,
            classpath: Seq[File] = null,
            sources: Seq[File] = null,
            srcDir: File = null,
            srcDirs: Seq[File] = null,
            destDir: File = null,
            encoding: String = "UTF-8",
            deprecation: java.lang.Boolean = null,
            verbose: java.lang.Boolean = null,
            source: String = null,
            target: String = null,
            debugInfo: String = null,
            fork: Boolean = false,
            additionalJavacArgs: Seq[String] = null)(implicit project: Project) =
    new Javac(
      compilerClasspath = compilerClasspath,
      classpath = classpath,
      sources = sources,
      srcDir = srcDir,
      srcDirs = srcDirs,
      destDir = destDir,
      encoding = encoding,
      deprecation = deprecation,
      verbose = verbose,
      source = source,
      target = target,
      debugInfo = debugInfo,
      fork = fork,
      additionalJavacArgs = additionalJavacArgs
    ).execute

}

/**
 * Java Compiler addon.
 *
 * The compiler can be configured via constructor parameter or `var`s.
 * To actually start the compilation use [[Javac#execute]].
 *
 * To easily configure and execute the compiler in one go, see [[Javac$#apply]].
 *
 * @since 0.4.0
 *
 * @constructor
 * Creates a new Javac Compiler addon instance.
 * All parameters can be omitted and set later.
 *
 * The source files can be given via multiple parameters, '''sources''', '''srcDir''' and '''srcDirs''', and will be joined.
 *
 * @param compilerClasspath The classpath which contains the compiler and its dependencies. If not given, the environment variable `JAVA_HOME` will be checked, and if it points to a installed JDK, this one will be used.
 * @param classpath The classpath used to load dependencies of the sources.
 * @param srcDir A directory containing Java source files.
 * @param srcDirs Multiple directories containing Java source files.
 * @param sources Source files to be compiled.
 * @param destDir The directory, where the compiled class files will be stored. If the directory does not exists, it will be created.
 * @param encoding The encoding of the source files.
 * @param deprecation Output source locations where deprecated APIs are used.
 * @param verbose Output messages about what the compiler is doing.
 * @param source Provide source compatibility with specified release.
 * @param target Generate class files for the specified VM version.
 * @param debugInfo If specified generate debugging info. Supported values: none, lines, vars, source, all.
 * @param fork Run the compile in a separate process (if `true`).
 * @param additionalJavacArgs Additional arguments directly passed to the Java compiler. Refer to the javac manual or inspect `javac -help` output.
 *
 */

class Javac(
    var compilerClasspath: Seq[File] = null,
    var classpath: Seq[File] = null,
    var sources: Seq[File] = null,
    var srcDir: File = null,
    var srcDirs: Seq[File] = null,
    var destDir: File = null,
    var encoding: String = "UTF-8",
    var deprecation: java.lang.Boolean = null,
    var verbose: java.lang.Boolean = null,
    var source: String = null,
    var target: String = null,
    var debugInfo: String = null,
    var fork: Boolean = false,
    var additionalJavacArgs: Seq[String] = null)(implicit project: Project) {

  private[this] val log = Logger[Javac]

  val javacClassName = "com.sun.tools.javac.Main"

  override def toString(): String = getClass.getSimpleName +
    "(compilerClasspath=" + compilerClasspath +
    ",classpath=" + classpath +
    ",sources=" + sources +
    ",srcDir=" + srcDir +
    ",srdDirs=" + srcDirs +
    ",destDir=" + destDir +
    ",encoding=" + encoding +
    ",deprecation=" + deprecation +
    ",verbose=" + verbose +
    ",source=" + source +
    ",target=" + target +
    ",debugInfo=" + debugInfo +
    ",fork=" + fork +
    ",additionalJavacArgs=" + additionalJavacArgs +
    ")"

  /**
   * Execute the Java compiler.
   */
  def execute {
    log.debug("About to execute " + this)

    var args = Array[String]()

    if (classpath != null) {
      val cPath = ForkSupport.pathAsArg(classpath)
      log.debug("Using classpath: " + cPath)
      args ++= Array("-classpath", cPath)
    }

    if (destDir != null) args ++= Array("-d", destDir.getAbsolutePath)

    if (encoding != null) args ++= Array("-encoding", encoding)
    if (deprecation != null && deprecation.booleanValue) args ++= Array("-deprecation")
    if (verbose != null && verbose.booleanValue) args ++= Array("-verbose")
    if (source != null) args ++= Array("-source", source)
    if (target != null) args ++= Array("-target", target)
    if (debugInfo != null) {
      debugInfo.trim match {
        case "" | "all" => args ++= Array("-g")
        case arg => args ++= Array("-g:" + arg)
      }
    }

    if (additionalJavacArgs != null && !additionalJavacArgs.isEmpty) args ++= additionalJavacArgs

    var allSrcDirs = Seq[File]()
    if (srcDir != null) allSrcDirs ++= Seq(srcDir)
    if (srcDirs != null) allSrcDirs ++= srcDirs
    require(!allSrcDirs.isEmpty || !sources.isEmpty, "No source path(s) and no sources set.")

    val sourceFiles: Seq[File] =
      (if (sources == null) Seq() else sources) ++
        allSrcDirs.flatMap { dir =>
          log.debug("Search files in dir: " + dir)
          val files = Util.recursiveListFiles(dir, """.*\.java$""".r)
          log.debug("Found files: " + files.mkString(", "))
          files
        }

    if (!sourceFiles.isEmpty) {
      val absSourceFiles = sourceFiles.map(f => f.getAbsolutePath)
      log.debug("Found source files: " + absSourceFiles.mkString(", "))
      args ++= absSourceFiles
    }

    project.monitor.info(CmdlineMonitor.Default, s"Compiling ${sourceFiles.size} Java source files to ${destDir}")

    if (destDir != null && !sourceFiles.isEmpty) destDir.mkdirs

    if (compilerClasspath == null || compilerClasspath == Seq()) {
      val javaHome = System.getenv("JAVA_HOME")
      if (javaHome != null) compilerClasspath = Seq(Path(javaHome, "lib", "tools.jar"))
      else compilerClasspath = Seq()
    }

    val result =
      if (fork) compileExternal(args)
      else compileInternal(args)

    if (result != 0) {
      val ex = new ExecutionFailedException("Compile Errors. See compiler output.")
      ex.buildScript = Some(project.projectFile)
      throw ex
    }

  }

  protected def compileExternal(args: Array[String]): Int =
    ForkSupport.runJavaAndWait(compilerClasspath, Array(javacClassName) ++ args)

  protected def compileInternal(args: Array[String]): Int = {

    val compilerClassLoader = compilerClasspath match {
      case Seq() =>
        classOf[Javac].getClassLoader
      case cp =>
        val cl = new URLClassLoader(cp.map { f => f.toURI().toURL() }.toArray, classOf[Javac].getClassLoader)
        log.debug("Using addional compiler classpath: " + cl.getURLs().mkString(", "))
        cl
    }

    val arrayInstance = java.lang.reflect.Array.newInstance(classOf[String], args.size)
    0.to(args.size - 1).foreach { i =>
      java.lang.reflect.Array.set(arrayInstance, i, args(i))
    }
    val arrayClass = arrayInstance.getClass

    val compilerClass = compilerClassLoader.loadClass(javacClassName)
    val compiler = compilerClass.newInstance
    val compileMethod = compilerClass.getMethod("compile", Array(arrayClass): _*)
    compileMethod.invoke(compiler, arrayInstance).asInstanceOf[java.lang.Integer].intValue
  }
}

