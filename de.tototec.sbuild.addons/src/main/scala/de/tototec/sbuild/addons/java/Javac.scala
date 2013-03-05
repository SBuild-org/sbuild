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

object Javac {
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

  def compileExternal(args: Array[String]): Int =
    ForkSupport.runJavaAndWait(compilerClasspath, Array(javacClassName) ++ args)

  def compileInternal(args: Array[String]): Int = {

    val compilerClassLoader = compilerClasspath match {
      case Seq() =>
        classOf[Javac].getClassLoader
      case cp =>
        val cl = new URLClassLoader(cp.map { f => f.toURI().toURL() }.toArray, classOf[Javac].getClassLoader)
        project.log.log(LogLevel.Debug, "Using addional compiler classpath: " + cl.getURLs().mkString(", "))
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

