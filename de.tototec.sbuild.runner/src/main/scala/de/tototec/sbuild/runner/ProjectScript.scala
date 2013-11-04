package de.tototec.sbuild.runner

import java.io.File
import java.io.FileInputStream
import java.io.FileWriter
import java.lang.reflect.Method
import java.net.URL
import java.net.URLClassLoader
import scala.Array.canBuildFrom
import scala.io.BufferedSource
import de.tototec.sbuild.BuildfileCompilationException
import de.tototec.sbuild.CmdlineMonitor
import de.tototec.sbuild.ExportDependencies
import de.tototec.sbuild.Logger
import de.tototec.sbuild.OutputStreamCmdlineMonitor
import de.tototec.sbuild.Path
import de.tototec.sbuild.Project
import de.tototec.sbuild.ProjectConfigurationException
import de.tototec.sbuild.RichFile.toRichFile
import de.tototec.sbuild.SBuildException
import de.tototec.sbuild.SBuildVersion
import de.tototec.sbuild.Target
import de.tototec.sbuild.TargetRef
import de.tototec.sbuild.TargetRef.fromString
import de.tototec.sbuild.TargetRefs
import de.tototec.sbuild.TargetRefs.fromSeq
import de.tototec.sbuild.execute.ExecutedTarget
import de.tototec.sbuild.execute.InMemoryTransientTargetCache
import de.tototec.sbuild.execute.TargetExecutor
import de.tototec.sbuild.internal.BuildFileProject
import de.tototec.sbuild.internal.OSGiVersion
import de.tototec.sbuild.execute.ParallelExecContext

object ProjectScript {

  val InfoFileName = "sbuild.info.xml"

  case class CachedScalaCompiler(compilerClass: Class[_], compilerMethod: Method, reporterMethod: Method)
  case class CachedExtendedScalaCompiler(compilerClass: Class[_], compilerMethod: Method, reporterMethod: Method, outputMethod: Method, clearOutputMethod: Method)

  /**
   * Drop all caches. For now, this is the Scalac compiler and its ClassLoader.
   */
  def dropCaches { cachedScalaCompiler = None; cachedExtendedScalaCompiler = None }
  /**
   * Cached instance of the Scalac compiler class and its ClassLoader.
   * Using a cache will provide the benefit, that loading is faster and we potentially profit from any JIT-compilation at runtime.
   */
  private var cachedScalaCompiler: Option[CachedScalaCompiler] = None
  private var cachedExtendedScalaCompiler: Option[CachedExtendedScalaCompiler] = None

}

class ProjectScript(_scriptFile: File,
                    sbuildClasspath: Array[String],
                    compileClasspath: Array[String],
                    additionalProjectCompileClasspath: Array[String],
                    additionalProjectRuntimeClasspath: Array[String],
                    compilerPluginJars: Array[String],
                    noFsc: Boolean,
                    monitor: CmdlineMonitor,
                    fileLocker: FileLocker) {

  private[this] val log = Logger[ProjectScript]

  import ProjectScript._

  private[this] val annotationReader = new AnnotationReader()

  def this(scriptFile: File,
           classpathConfig: ClasspathConfig,
           monitor: CmdlineMonitor,
           fileLocker: FileLocker) {
    this(_scriptFile = scriptFile,
      sbuildClasspath = classpathConfig.sbuildClasspath,
      compileClasspath = classpathConfig.compileClasspath,
      additionalProjectCompileClasspath = classpathConfig.projectCompileClasspath,
      additionalProjectRuntimeClasspath = classpathConfig.projectRuntimeClasspath,
      compilerPluginJars = classpathConfig.compilerPluginJars,
      noFsc = classpathConfig.noFsc,
      monitor = monitor,
      fileLocker = fileLocker)
  }

  private[this] val scriptFile: File = Path.normalize(_scriptFile)
  require(scriptFile.isFile, "scriptFile must be a file")
  private[this] val projectDir: File = scriptFile.getParentFile

  private[this] val buildTargetDir = ".sbuild";
  private[this] val buildFileTargetDir = ".sbuild/scala/" + scriptFile.getName;

  private[this] val scriptBaseName = scriptFile.getName.endsWith(".scala") match {
    case true => scriptFile.getName.substring(0, scriptFile.getName.length - 6)
    case false =>
      log.debug("Scriptfile name does not end in '.scala'")
      scriptFile.getName
  }
  private[this] lazy val targetBaseDir: File = new File(scriptFile.getParentFile, buildTargetDir)
  private[this] lazy val targetDir: File = new File(scriptFile.getParentFile, buildFileTargetDir)
  private[this] val defaultTargetClassName = scriptBaseName
  private[this] def targetClassFile(targetClassName: String): File = new File(targetDir, targetClassName + ".class")
  // lazy val targetClassFile = new File(targetDir, scriptBaseName + ".class")
  private[this] lazy val infoFile = new File(targetDir, InfoFileName)

  /** The lock file used to synchronize compilation of the build file by multiple processes. */
  private[this] val lockFile = new File(targetBaseDir, "lock/" + scriptFile.getName + ".lock")

  /** File that contains the map from Scala type to the containing source file. */
  val typesToIncludedFilesPropertiesFile: File = new File(targetDir, "analyzedtypes.properties")

  private[this] def checkFile = if (!scriptFile.exists) {
    throw new ProjectConfigurationException(s"Could not find build file: ${scriptFile.getName}\n" +
      s"Searched in: ${scriptFile.getAbsoluteFile.getParent}")
  }

  /**
   * Compile this project script (if necessary) and apply it to the given Project.
   */
  def compileAndExecute(project: Project): Any = try {
    checkFile

    // We get an iterator and convert it to a stream, which will cache all seen lines
    val sourceStream = new BufferedSource(new FileInputStream(scriptFile)).getLines().toStream
    // Now we create an iterator which utilizes the already lazy and potentially cached stream
    def buildScriptIterator = sourceStream.iterator

    val versionOption = annotationReader.findFirstAnnotationSingleValue(buildScriptIterator, "version", "value")
    val version = versionOption.getOrElse("")
    val osgiVersion = OSGiVersion.parseVersion(version)
    if (osgiVersion.compareTo(new OSGiVersion(SBuildVersion.osgiVersion)) > 0) {
      throw new SBuildException("The buildscript '" + scriptFile + "' requires at least SBuild version: " + version)
    }

    log.debug("About to find additional classpath entries for: " + scriptFile)
    val cpEntries = annotationReader.
      findFirstAnnotationWithVarArgValue(buildScriptIterator, annoName = "classpath", varArgValueName = "value").
      map(_.values).getOrElse(Array())
    if (!cpEntries.isEmpty) {
      log.debug(scriptFile + " contains @classpath annotation: " + cpEntries.mkString("@classpath(", ",\n  ", ")"))
    }

    log.debug("About to find include files for: " + scriptFile)
    val includeEntires = annotationReader.
      findFirstAnnotationWithVarArgValue(buildScriptIterator, annoName = "include", varArgValueName = "value").
      map(_.values).getOrElse(Array())
    if (!includeEntires.isEmpty) {
      log.debug(scriptFile + " contains @include annotation: " + includeEntires.mkString("@include(", ",\n  ", ")"))
    }

    //    val toResolve = cpEntries.toSeq ++ includeEntires.toSeq
    val resolvedFiles = {
      val ts = System.currentTimeMillis
      val resolvedFiles = resolveViaProject(project, cpEntries, includeEntires)
      log.debug("Resolving project prerequisites took " + (System.currentTimeMillis - ts) + " milliseconds")
      resolvedFiles
    }

    val additionalClasspath = resolvedFiles.classpath.map(_.getPath).toArray
    val includes = resolvedFiles.includes

    val addCompileCp: Array[String] = additionalClasspath ++ additionalProjectCompileClasspath
    val addRuntimeCp: Array[String] = additionalClasspath ++ additionalProjectRuntimeClasspath

    // TODO: also check additional classpath entries 

    def compileWhenNecessary(checkLock: Boolean): String =
      checkInfoFileUpToDate(includes) match {
        case LastRunInfo(true, className, _) =>
          log.debug("Using previously compiled and up-to-date build class: " + className)
          className
        case LastRunInfo(_, _, reason) if !checkLock =>
          log.debug("Compiling build script " + scriptFile + " is necessary. Reason: " + reason)
          newCompile(sbuildClasspath ++ addCompileCp, includes, reason)
        case LastRunInfo(_, _, reason) =>
          fileLocker.acquire(
            file = lockFile,
            timeoutMsec = 30000,
            processInformation = s"SBuild ${SBuildVersion.osgiVersion} for project file: ${scriptFile}",
            onFirstWait = () =>
              monitor.info(CmdlineMonitor.Default, "Waiting for another SBuild process to release the build file: " + scriptFile),
            onDeleteOrphanLock = () =>
              log.debug("Deleting orphan lock file: " + lockFile)
          ) match {
              case Right(fileLock) => try {
                log.debug("Acquired lock for script file: " + scriptFile)
                compileWhenNecessary(false)
              } finally {
                log.debug("Releasing lock for script file: " + scriptFile)
                fileLock.release
              }
              case Left(reason) => {
                log.error("Could not acquire lock for script file: " + scriptFile + ". Reason. " + reason)
                throw new BuildfileCompilationException("Buildfile compilation is locked by another process: " + reason)
              }
            }
      }

    val buildClassName = compileWhenNecessary(checkLock = true)

    // Experimental: Attach included files 
    {
      implicit val _p = project
      val includedFiles = includes.flatMap { case (k, v) => v }.map(TargetRef(_)).toSeq
      ExportDependencies("sbuild.project.includes", TargetRefs.fromSeq(includedFiles))
    }

    useExistingCompiled(project, addRuntimeCp, buildClassName)
  } catch {
    case e: SBuildException =>
      e.buildScript = Some(project.projectFile)
      throw e
  }

  case class LastRunInfo(upToDate: Boolean, targetClassName: String, issues: Option[String] = None)

  def checkInfoFileUpToDate(includes: Map[String, Seq[File]]): LastRunInfo = {
    if (!infoFile.exists()) LastRunInfo(false, defaultTargetClassName)
    else {
      val info = xml.XML.loadFile(infoFile)

      val sourceSize = (info \ "sourceSize").text.toLong
      val sourceLastModified = (info \ "sourceLastModified").text.toLong
      val targetClassName = (info \ "targetClassName").text match {
        case "" | null => defaultTargetClassName
        case x => x
      }
      val targetClassLastModified = (info \ "targetClassLastModified").text.toLong
      val sbuildVersion = (info \ "sbuildVersion").text
      val sbuildOsgiVersion = (info \ "sbuildOsgiVersion").text

      val sbuildVersionMatch = sbuildVersion == SBuildVersion.version && sbuildOsgiVersion == SBuildVersion.osgiVersion

      val classFile = targetClassFile(targetClassName)
      val scriptFileUpToDate = scriptFile.length == sourceSize &&
        scriptFile.lastModified == sourceLastModified &&
        classFile.lastModified == targetClassLastModified &&
        classFile.lastModified >= scriptFile.lastModified

      lazy val includesMatch: Boolean = try {
        val lastIncludes = (info \ "includes" \ "include").map { lastInclude =>
          ((lastInclude \ "path").text, (lastInclude \ "lastModified").text.toLong)
        }.toMap

        val flatIncludes = includes.flatMap { case (key, value) => value }

        flatIncludes.size == lastIncludes.size &&
          flatIncludes.forall { file =>
            lastIncludes.get(file.getPath()) match {
              case Some(time) => file.lastModified == time
              case _ => false
            }
          }
      } catch {
        case e: Exception =>
          log.debug("Could not evaluate up-to-date state of included files.", e)
          false
      }

      LastRunInfo(
        upToDate = sbuildVersionMatch && scriptFileUpToDate && includesMatch,
        targetClassName = targetClassName,
        issues = (sbuildVersionMatch, scriptFileUpToDate, includesMatch) match {
          case (false, _, _) => Some(s"SBuild version changed (${sbuildVersion} -> ${SBuildVersion.version})")
          case (_, false, _) => None
          case (_, _, false) => Some("Includes changed")
          case _ => None
        }
      )
    }
  }

  //  protected def readIncludeFiles(project: Project, buildScript: => Iterator[String]): Map[String, Seq[File]] = {
  //    log.debug("About to find include files.")
  //    val cp = annotationReader.findFirstAnnotationWithVarArgValue(buildScript, annoName = "include", varArgValueName = "value").map(_.values).getOrElse(Array())
  //    log.debug(if (cp.isEmpty) "No includes files specified." else s"Using ${cp.size} included files:\n - ${cp.mkString("\n - ")}")
  //
  //    // TODO: specific error message, when fetch or download fails
  //    resolveViaProject(cp, project, "@include")
  //  }

  case class ResolvedPrerequisites(classpath: Seq[File], includes: Map[String, Seq[File]])

  protected def resolveViaProject(project: Project, classpathTargets: Seq[String], includeTargets: Seq[String]): ResolvedPrerequisites = {
    if (classpathTargets.isEmpty && includeTargets.isEmpty) return ResolvedPrerequisites(Seq(), Map())

    // We want to use a customized monitor
    val resolveMonitor = new OutputStreamCmdlineMonitor(Console.out, mode = project.monitor.mode, messagePrefix = "(init) ")

    // We want to use a dedicated project in the init phase
    class ProjectInitProject extends BuildFileProject(_projectFile = project.projectFile, monitor = resolveMonitor)
    implicit val initProject: Project = new ProjectInitProject

    val resolverTargetSuffix = project match {
      case p: BuildFileProject => p.projectPool.formatProjectFileRelativeToBase(project)
      case _ => scriptFile
    }

    val cpDep = classpathTargets.map(TargetRef(_)) match {
      case Seq() => Seq()
      case deps => Seq(TargetRef(Target("phony:@classpath " + resolverTargetSuffix) dependsOn deps))
    }
    val includeDep = includeTargets.map(TargetRef(_)) match {
      case Seq() => Seq()
      case deps => Seq(TargetRef(Target("phony:@include " + resolverTargetSuffix) dependsOn deps))
    }

    val resolverTarget = Target("phony:@init") dependsOn cpDep ++ includeDep

    val targetExecutor = new TargetExecutor(
      monitor = initProject.monitor,
      monitorConfig = TargetExecutor.MonitorConfig(
        executing = CmdlineMonitor.Verbose,
        topLevelSkipped = CmdlineMonitor.Verbose
      ))

    val dryRun: ExecutedTarget = targetExecutor.preorderedDependenciesTree(
      curTarget = resolverTarget,
      transientTargetCache = Some(new InMemoryTransientTargetCache()),
      skipExec = true
    )

    val execProgressOption = Some(new TargetExecutor.ExecProgress(maxCount = dryRun.treeSize))

    val executedResolverTarget = targetExecutor.preorderedDependenciesTree(
      curTarget = resolverTarget,
      transientTargetCache = Some(new InMemoryTransientTargetCache()),
      execProgress = execProgressOption,
      parallelExecContext = Some(new ParallelExecContext(threadCount = None))
    )

    val resolvedFiles = executedResolverTarget.dependencies.flatMap { cpOrIncludeT =>
      cpOrIncludeT.dependencies.map { t =>
        t.targetContext.target.name -> t.targetContext.targetFiles
      }
    }.toMap

    val additionalClasspath = resolvedFiles.filterKeys(k => classpathTargets.contains(k)).values.flatten.toSeq
    if (!additionalClasspath.isEmpty) log.debug("Resolved @classpath to: " + additionalClasspath.mkString(":"))

    val includes = resolvedFiles.filterKeys(k => includeTargets.contains(k))
    if (!includes.isEmpty) log.debug("Resolved @include to: " + includes)

    ResolvedPrerequisites(additionalClasspath, includes)

    //    resolvedFiles
  }

  //  protected def readAdditionalClasspath(project: Project, buildScript: => Iterator[String]): Array[String] = {
  //    log.debug("About to find additional classpath entries.")
  //    val cp = annotationReader.findFirstAnnotationWithVarArgValue(buildScript, annoName = "classpath", varArgValueName = "value").map(_.values).getOrElse(Array())
  //    log.debug("Using additional classpath entries: " + cp.mkString(", "))
  //    resolveViaProject(cp, project, "@classpath").flatMap { case (key, value) => value }.map { _.getPath }.toArray
  //  }

  protected def useExistingCompiled(project: Project, classpath: Array[String], className: String): Any = {
    log.debug("Loading compiled version of build script: " + scriptFile)
    val cl = new URLClassLoader(Array(targetDir.toURI.toURL) ++ classpath.map(cp => new File(cp).toURI.toURL), getClass.getClassLoader)
    log.debug("ClassLoader loads build script from URLs: " + cl.asInstanceOf[{ def getURLs: Array[URL] }].getURLs.mkString(",\n  "))
    val clazz: Class[_] = cl.loadClass(className)
    val ctr = clazz.getConstructor(classOf[Project])
    val scriptInstance = ctr.newInstance(project)
    // We assume, that everything is done in constructor, so we are done here
    project.applyPlugins
    scriptInstance
  }

  def clean(): Unit = if (targetBaseDir.exists) {
    monitor.info(CmdlineMonitor.Verbose, "Deleting dir_ " + targetBaseDir)
    targetBaseDir.deleteRecursive
  }

  def cleanScala(): Unit = if(targetDir.exists) {
    monitor.info(CmdlineMonitor.Verbose, "Deleting dir_ " + targetDir)
    targetDir.deleteRecursive
  }

  protected def newCompile(classpath: Array[String], includes: Map[String, Seq[File]], printReason: Option[String] = None): String = {
    cleanScala()
    targetDir.mkdirs
    monitor.info(CmdlineMonitor.Default,
      (printReason match {
        case None => ""
        case Some(r) => r + ": "
      }) + "Compiling build script: " + scriptFile +
        (if (includes.isEmpty) "" else " and " + includes.size + " included files") +
        "..."
    )

    compile(classpath.mkString(File.pathSeparator), includes)

    val (realTargetClassName, realTargetClassFile) = targetClassFile(defaultTargetClassName) match {
      case classExists if classExists.exists() => (defaultTargetClassName, classExists)
      case _ => ("SBuild", targetClassFile("SBuild"))
    }

    log.debug("Writing info file: " + infoFile)
    val info = <sbuild>
                 <sourceSize>{ scriptFile.length }</sourceSize>
                 <sourceLastModified>{ scriptFile.lastModified }</sourceLastModified>
                 <targetClassName>{ realTargetClassName }</targetClassName>
                 <targetClassLastModified>{ realTargetClassFile.lastModified }</targetClassLastModified>
                 <sbuildVersion>{ SBuildVersion.version }</sbuildVersion>
                 <sbuildOsgiVersion>{ SBuildVersion.osgiVersion }</sbuildOsgiVersion>
                 <includes>
                   {
                     includes.map {
                       case (key, value) =>
                         log.debug(s"""@include("${key}") resolved to ${value.size} files: ${value.mkString(", ")}""")
                         value.map { file =>
                           <include>
                             <path>{ file.getPath }</path>
                             <lastModified>{ file.lastModified }</lastModified>
                           </include>
                         }
                     }
                   }
                 </includes>
               </sbuild>
    val file = new FileWriter(infoFile)
    xml.XML.write(file, info, "UTF-8", true, null)
    file.close

    realTargetClassName
  }

  protected def compile(classpath: String, includes: Map[String, Seq[File]]) {
    val compilerPluginSettings = compilerPluginJars match {
      case Array() => Array[String]()
      case jars => jars.map { jar: String => "-Xplugin:" + jar }
    }
    val params = compilerPluginSettings ++ Array(
      "-P:analyzetypes:outfile=" + typesToIncludedFilesPropertiesFile.getPath(),
      "-classpath", classpath,
      "-deprecation",
      "-g:vars",
      "-d", targetDir.getPath,
      scriptFile.getPath) ++
      (includes.flatMap { case (name, files) => files }.map { _.getPath })

    lazy val lazyCompilerClassloader = {
      log.debug("Using additional classpath for scala compiler: " + compileClasspath.mkString(", "))
      new URLClassLoader(compileClasspath.map { f => new File(f).toURI.toURL }, getClass.getClassLoader)
    }

    def compileWithFsc {
      val compileClient = lazyCompilerClassloader.loadClass("scala.tools.nsc.StandardCompileClient").newInstance
      //      import scala.tools.nsc.StandardCompileClient
      //      val compileClient = new StandardCompileClient
      val compileMethod = compileClient.asInstanceOf[Object].getClass.getMethod("process", Array(classOf[Array[String]]): _*)
      log.debug("Executing CompileClient with args: " + params.mkString(" "))
      val retVal = compileMethod.invoke(compileClient, params).asInstanceOf[Boolean]
      if (!retVal) throw new BuildfileCompilationException("Could not compile build file " + scriptFile.getAbsolutePath + " with CompileClient. See compiler output.")
    }

    def compileWithoutFsc {

      val useExtendedCompiler = true
      if (useExtendedCompiler) {

        val cachedCompiler = ProjectScript.cachedExtendedScalaCompiler match {
          case Some(cached) =>
            log.debug("Reusing cached extended compiler instance.")
            cached
          case None =>
            val compiler = lazyCompilerClassloader.loadClass("de.tototec.sbuild.scriptcompiler.ScriptCompiler")
            //            val compiler = compilerClass.getConstructor().newInstance()
            val compilerMethod = compiler.getMethod("process", Array(classOf[Array[String]]): _*)
            val reporterMethod = compiler.getMethod("reporter")
            val outputMethod = compiler.getMethod("getRecordedOutput")
            val clearOutputMethod = compiler.getMethod("clearRecordedOutput")
            val cache = CachedExtendedScalaCompiler(compiler, compilerMethod, reporterMethod, outputMethod, clearOutputMethod)
            log.debug("Caching extended compiler for later use.")
            ProjectScript.cachedExtendedScalaCompiler = Some(cache)
            cache
        }

        log.debug("Executing Scala Compile with args: " + params.mkString(" "))
        val compilerInstance = cachedCompiler.compilerClass.getConstructor().newInstance()

        cachedCompiler.compilerMethod.invoke(compilerInstance, params)
        val reporter = cachedCompiler.reporterMethod.invoke(compilerInstance)
        val hasErrors = reporter.asInstanceOf[{ def hasErrors(): Boolean }].hasErrors
        if (hasErrors) {
          val output = cachedCompiler.outputMethod.invoke(compilerInstance).asInstanceOf[Seq[String]]
          cachedCompiler.clearOutputMethod.invoke(compilerInstance)
          throw new BuildfileCompilationException("Could not compile build file " + scriptFile.getAbsolutePath + " with scala compiler.\nCompiler output:\n" + output.mkString("\n"))
        }
        cachedCompiler.clearOutputMethod.invoke(compilerInstance)

      } else {
        val cachedCompiler = ProjectScript.cachedScalaCompiler match {
          case Some(cached) =>
            log.debug("Reusing cached compiler instance.")
            cached
          case None =>
            val compiler = lazyCompilerClassloader.loadClass("scala.tools.nsc.Main")
            val compilerMethod = compiler.getMethod("process", Array(classOf[Array[String]]): _*)
            val reporterMethod = compiler.getMethod("reporter")
            val cache = CachedScalaCompiler(compiler, compilerMethod, reporterMethod)
            log.debug("Caching compiler for later use.")
            ProjectScript.cachedScalaCompiler = Some(cache)
            cache
        }

        log.debug("Executing Scala Compile with args: " + params.mkString(" "))
        cachedCompiler.compilerMethod.invoke(null, params)
        val reporter = cachedCompiler.reporterMethod.invoke(null)
        val hasErrors = reporter.asInstanceOf[{ def hasErrors(): Boolean }].hasErrors
        if (hasErrors) throw new BuildfileCompilationException("Could not compile build file " + scriptFile.getAbsolutePath + " with scala compiler. See compiler output.")
      }
    }

    if (noFsc) {
      compileWithoutFsc
    } else {
      try {
        compileWithFsc
      } catch {
        case e: SBuildException => throw e
        case e: Exception =>
          log.debug("Compilation with CompileClient failed. trying non-distributed Scala compiler.")
          compileWithoutFsc
      }
    }
  }

}
