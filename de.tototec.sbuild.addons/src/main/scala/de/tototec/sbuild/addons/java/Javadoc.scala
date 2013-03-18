package de.tototec.sbuild.addons.java

import java.io.File
import java.net.URLClassLoader
import de.tototec.sbuild.Project
import de.tototec.sbuild.LogLevel
import de.tototec.sbuild.ExecutionFailedException
import de.tototec.sbuild.Util
import de.tototec.sbuild.Path
import de.tototec.sbuild.addons.support.ForkSupport
import java.lang.reflect.Method

/**
 * Javadoc Generator Addon.
 *
 * Use [[de.tototec.sbuild.addons.java.Javadoc$#apply]] to configure and execute it in one go.
 *
 */
object Javadoc {

  /**
   * Creates, configures and executes the Javadoc Addon.
   *
   * For parameter documentation see the [[Javadoc]] constructor.
   *
   * @since 0.4.1
   */
  def apply(javadocClasspath: Seq[File] = null,
            classpath: Seq[File] = null,
            sources: Seq[File] = null,
            srcDir: File = null,
            srcDirs: Seq[File] = null,
            destDir: File = null,
            encoding: String = "UTF-8",
            verbose: java.lang.Boolean = null,
            source: String = null,
            debugInfo: String = null,
            fork: Boolean = false,
            additionalJavadocArgs: Seq[String] = null)(implicit project: Project) =
    new Javadoc(
      javadocClasspath = javadocClasspath,
      classpath = classpath,
      sources = sources,
      srcDir = srcDir,
      srcDirs = srcDirs,
      destDir = destDir,
      encoding = encoding,
      verbose = verbose,
      source = source,
      debugInfo = debugInfo,
      fork = fork,
      additionalJavadocArgs = additionalJavadocArgs
    ).execute

}

/**
 * Javadoc Generator Addon.
 *
 * The generator can be configured via constructor parameter or `var`s.
 * To actually start the compilation use [[Javac#execute]].
 *
 * To easily configure and execute the compiler in one go, see [[Javadoc$#apply]].
 *
 * @since 0.4.1
 *
 * @constructor
 * Creates a new Javadoc Generator addon instance.
 * All parameters can be omitted and set later.
 *
 * The source files can be given via multiple parameters, '''sources''', '''srcDir''' and '''srcDirs''', and will be joined.
 *
 * @param javadocClasspath The classpath which contains the Javadoc generator and its dependencies. If not given, the environment variable `JAVA_HOME` will be checked, and if it points to a installed JDK, this one will be used.
 * @param classpath The classpath used to load dependencies of the sources.
 * @param srcDir A directory containing Java source files.
 * @param srcDirs Multiple directories containing Java source files.
 * @param sources Source files.
 * @param destDir The directory, where the generated files will be stored.
 *   If the directory does not exists, it will be created.
 * @param encoding The encoding of the source files.
 * @param verbose Output messages about what the generator is doing.
 * @param source Provide source compatibility with specified release.
 * @param debugInfo If specified generate debugging info. 
 *   Supported values: none, lines, vars, source, all.
 * @param fork Run the generator in a separate process (if `true`).
 * @param additionalJavacArgs Additional arguments directly passed to the Javadoc generator.
 *   Refer to the javadoc manual or inspect `javadoc -help` output.
 *
 */

class Javadoc(
  var javadocClasspath: Seq[File] = null,
  var classpath: Seq[File] = null,
  var sources: Seq[File] = null,
  var srcDir: File = null,
  var srcDirs: Seq[File] = null,
  var destDir: File = null,
  var encoding: String = "UTF-8",
  var verbose: java.lang.Boolean = null,
  var source: String = null,
  var debugInfo: String = null,
  var fork: Boolean = false,
  var additionalJavadocArgs: Seq[String] = null)(implicit project: Project) {

  val javadocClassName = "com.sun.tools.javadoc.Main"

  override def toString(): String = getClass.getSimpleName +
    "(javadocClasspath=" + javadocClasspath +
    ",classpath=" + classpath +
    ",sources=" + sources +
    ",srcDir=" + srcDir +
    ",srdDirs=" + srcDirs +
    ",destDir=" + destDir +
    ",encoding=" + encoding +
    ",verbose=" + verbose +
    ",source=" + source +
    ",debugInfo=" + debugInfo +
    ",fork=" + fork +
    ",additionalJavadocArgs=" + additionalJavadocArgs +
    ")"

  /**
   * Execute the Javadoc generator.
   */
  def execute {
    project.log.log(LogLevel.Debug, "About to execute " + this)

    var args = Array[String]()

    if (classpath != null) {
      val cPath = ForkSupport.pathAsArg(classpath)
      project.log.log(LogLevel.Debug, "Using classpath: " + cPath)
      args ++= Array("-classpath", cPath)
    }

    if (destDir != null) args ++= Array("-d", destDir.getAbsolutePath)

    if (encoding != null) args ++= Array("-encoding", encoding)
    if (verbose != null && verbose.booleanValue) args ++= Array("-verbose")
    if (source != null) args ++= Array("-source", source)
    if (debugInfo != null) {
      debugInfo.trim match {
        case "" | "all" => args ++= Array("-g")
        case arg => args ++= Array("-g:" + arg)
      }
    }

    if (additionalJavadocArgs != null && !additionalJavadocArgs.isEmpty) args ++= additionalJavadocArgs

    var allSrcDirs = Seq[File]()
    if (srcDir != null) allSrcDirs ++= Seq(srcDir)
    if (srcDirs != null) allSrcDirs ++= srcDirs
    require(!allSrcDirs.isEmpty || !sources.isEmpty, "No source path(s) and no sources set.")

    val sourceFiles: Seq[File] =
      (if (sources == null) Seq() else sources) ++
        allSrcDirs.flatMap { dir =>
          project.log.log(LogLevel.Debug, "Search files in dir: " + dir)
          val files = Util.recursiveListFiles(dir, """.*\.java$""".r)
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

    if (javadocClasspath == null || javadocClasspath == Seq()) {
      val javaHome = System.getenv("JAVA_HOME")
      if (javaHome != null) javadocClasspath = Seq(Path(javaHome, "lib", "tools.jar"))
      else javadocClasspath = Seq()
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
    ForkSupport.runJavaAndWait(javadocClasspath, Array(javadocClassName) ++ args)

  protected def compileInternal(args: Array[String]): Int = {

    val javadocClassLoader = javadocClasspath match {
      case Seq() =>
        classOf[Javac].getClassLoader
      case cp =>
        val cl = new URLClassLoader(cp.map { f => f.toURI().toURL() }.toArray, classOf[Javac].getClassLoader)
        project.log.log(LogLevel.Debug, "Using addional javadoc classpath: " + cl.getURLs().mkString(", "))
        cl
    }

    val arrayInstance = java.lang.reflect.Array.newInstance(classOf[String], args.size)
    0.to(args.size - 1).foreach { i =>
      java.lang.reflect.Array.set(arrayInstance, i, args(i))
    }
    val arrayClass = arrayInstance.getClass

    val javadocClass = javadocClassLoader.loadClass(javadocClassName)
    //val javadoc = javadocClass.newInstance
    val generateMethod = javadocClass.getMethod("execute", Array(classOf[ClassLoader], arrayClass): _*)
    generateMethod.invoke(null, javadocClassLoader, arrayInstance).asInstanceOf[java.lang.Integer].intValue
  }
}

