package de.tototec.sbuild.ant.tasks

import de.tototec.sbuild.Project
import org.apache.tools.ant.taskdefs.Javac
import de.tototec.sbuild.ant.AntProject
import org.apache.tools.ant.types.{ Path => APath }
import java.io.File

object AntJavac {
  def apply(srcDir: APath = null,
            destDir: File = null,
            fork: java.lang.Boolean = null,
            source: String = null,
            target: String = null,
            encoding: String = null,
            includeAntRuntime: java.lang.Boolean = null,
            debug: java.lang.Boolean = null,
            optimize: java.lang.Boolean = null,
            classpath: APath = null,
            debugLevel: String = null,
            includes: String = null,
            includesFile: File = null,
            excludes: String = null,
            excludesFile: File = null,
            noWarn: java.lang.Boolean = null,
            deprecation: java.lang.Boolean = null,
            verbose: java.lang.Boolean = null,
            depend: java.lang.Boolean = null,
            includeJavaRuntime: java.lang.Boolean = null,
            executable: String = null,
            memoryInitialSize: String = null,
            memoryMaximumSize: String = null,
            failOnError: java.lang.Boolean = null,
            compiler: String = null,
            listFiles: java.lang.Boolean = null,
            tempDir: File = null,
            includeDestClasses: java.lang.Boolean = null,
            createMissingPackageInfoClass: java.lang.Boolean = null)(implicit project: Project) =
    new AntJavac(
      srcDir = srcDir,
      destDir = destDir,
      fork = fork,
      source = source,
      target = target,
      encoding = encoding,
      includeAntRuntime = includeAntRuntime,
      debug = debug,
      optimize = optimize,
      classpath = classpath,
      debugLevel = debugLevel,
      includes = includes,
      includesFile = includesFile,
      excludes = excludes,
      excludesFile = excludesFile,
      noWarn = noWarn,
      deprecation = deprecation,
      verbose = verbose,
      depend = depend,
      includeJavaRuntime = includeJavaRuntime,
      executable = executable,
      memoryInitialSize = memoryInitialSize,
      memoryMaximumSize = memoryMaximumSize,
      failOnError = failOnError,
      compiler = compiler,
      listFiles = listFiles,
      tempDir = tempDir,
      includeDestClasses = includeDestClasses,
      createMissingPackageInfoClass = createMissingPackageInfoClass
    ).execute
}

class AntJavac()(implicit _project: Project) extends Javac {
  setProject(AntProject())

  def this(srcDir: APath = null,
           destDir: File = null,
           fork: java.lang.Boolean = null,
           source: String = null,
           target: String = null,
           encoding: String = null,
           includeAntRuntime: java.lang.Boolean = null,
           debug: java.lang.Boolean = null,
           optimize: java.lang.Boolean = null,
           classpath: APath = null,
           debugLevel: String = null,
           includes: String = null,
           includesFile: File = null,
           excludes: String = null,
           excludesFile: File = null,
           // since 0.1.0.9001
           noWarn: java.lang.Boolean = null,
           deprecation: java.lang.Boolean = null,
           verbose: java.lang.Boolean = null,
           depend: java.lang.Boolean = null,
           includeJavaRuntime: java.lang.Boolean = null,
           executable: String = null,
           memoryInitialSize: String = null,
           memoryMaximumSize: String = null,
           failOnError: java.lang.Boolean = null,
           compiler: String = null,
           listFiles: java.lang.Boolean = null,
           tempDir: File = null,
           includeDestClasses: java.lang.Boolean = null,
           createMissingPackageInfoClass: java.lang.Boolean = null)(implicit project: Project) {
    this
    if (srcDir != null) setSrcdir(srcDir)
    if (destDir != null) setDestdir(destDir)
    if (fork != null) setFork(fork.booleanValue)
    if (source != null) setSource(source)
    if (target != null) setTarget(target)
    if (encoding != null) setEncoding(encoding)
    if (includeAntRuntime != null) setIncludeantruntime(includeAntRuntime.booleanValue)
    if (debug != null) setDebug(debug.booleanValue)
    if (optimize != null) setOptimize(optimize.booleanValue)
    if (classpath != null) setClasspath(classpath)
    if (debugLevel != null) setDebugLevel(debugLevel)
    if (includes != null) setIncludes(includes)
    if (includesFile != null) setIncludesfile(includesFile)
    if (excludes != null) setExcludes(excludes)
    if (excludesFile != null) setExcludesfile(excludesFile)
    if (noWarn != null) setNowarn(noWarn.booleanValue)
    if (deprecation != null) setDeprecation(deprecation.booleanValue)
    if (verbose != null) setVerbose(verbose.booleanValue)
    if (depend != null) setDepend(depend.booleanValue)
    if (includeJavaRuntime != null) setIncludejavaruntime(includeJavaRuntime.booleanValue)
    if (executable != null) setExecutable(executable)
    if (memoryInitialSize != null) setMemoryInitialSize(memoryInitialSize)
    if (memoryMaximumSize != null) setMemoryMaximumSize(memoryMaximumSize)
    if (failOnError != null) setFailonerror(failOnError.booleanValue)
    if (compiler != null) setCompiler(compiler)
    if (listFiles != null) setListfiles(listFiles.booleanValue)
    if (tempDir != null) setTempdir(tempDir)
    if (includeDestClasses != null) setIncludeDestClasses(includeDestClasses.booleanValue)
    if (createMissingPackageInfoClass != null) setCreateMissingPackageInfoClass(createMissingPackageInfoClass.booleanValue)
  }

  def setDestDir(destDir: File) = setDestdir(destDir)
  def setIncludeAntRuntime(includeAntRuntime: Boolean) = setIncludeantruntime(includeAntRuntime)
  def setSrcDir(srcDir: APath) = setSrcdir(srcDir)
  def setIncludesFile(includesFile: File) = setIncludesfile(includesFile)
  def setExcludesFile(excludesFile: File) = setExcludesfile(excludesFile)
  def setNoWarn(noWarn: Boolean) = setNowarn(noWarn)
  def setIncludeJavaRuntime(includeJavaRuntime: Boolean) = setIncludejavaruntime(includeJavaRuntime)
  def setFailOnError(failOnError: Boolean) = setFailonerror(failOnError)
  def setListFiles(listFiles: Boolean) = setListfiles(listFiles)
  def setTempDir(tempDir: File) = setTempdir(tempDir)

}