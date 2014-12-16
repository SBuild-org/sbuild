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
import de.tototec.sbuild.Plugin
import de.tototec.sbuild.internal.I18n

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

// TODO: if the compiled buildfile is up-to-date, reuse already parsed info (sbuild.info.xml) about classpath and include instead of re-parsing it
class ProjectScript(_scriptFile: File,
                    sbuildClasspath: Array[String],
                    compileClasspath: Array[String],
                    additionalProjectCompileClasspath: Array[String],
                    additionalProjectRuntimeClasspath: Array[String],
                    compilerPluginJars: Array[String],
                    noFsc: Boolean,
                    monitor: CmdlineMonitor,
                    fileLocker: FileLocker) {

  import ProjectScript._

  private[this] val log = Logger[ProjectScript]
  private[this] val i18n = I18n[ProjectScript]
  import i18n._

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
  if (!scriptFile.exists || !scriptFile.isFile) {
    val msg = preparetr("Project buildfile \"{0}\" does not exist or is not a file.", scriptFile)
    val ex = new ProjectConfigurationException(msg.notr, null, msg.tr)
    ex.buildScript = Some(scriptFile)
    throw ex
  }

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

    new VersionChecker().assertBuildscriptVersion(version, scriptFile)

    log.debug("About to find additional classpath entries for: " + scriptFile)
    val allCpEntries = annotationReader.
      findFirstAnnotationWithVarArgValue(buildScriptIterator, annoName = "classpath", varArgValueName = "value").
      map(_.values).getOrElse(Array())
    if (!allCpEntries.isEmpty) {
      log.debug(scriptFile + " contains @classpath annotation: " + allCpEntries.mkString("@classpath(", ",\n  ", ")"))
    }

    //    val cpEntries = allCpEntries.map {
    //      case x if x.startsWith("raw:") => RawCpEntry(x.substring(4))
    //      case x => ExtensionCpEntry(x)
    //    }

    log.debug("About to find include files for: " + scriptFile)
    val includeEntires = annotationReader.
      findFirstAnnotationWithVarArgValue(buildScriptIterator, annoName = "include", varArgValueName = "value").
      map(_.values).getOrElse(Array())
    if (!includeEntires.isEmpty) {
      log.debug(scriptFile + " contains @include annotation: " + includeEntires.mkString("@include(", ",\n  ", ")"))
    }

    val resolved = {
      val ts = System.currentTimeMillis

      val classpathResolver = new ClasspathResolver(project)
      val resolved = classpathResolver(ClasspathResolver.ResolveRequest(includeEntries = includeEntires, classpathEntries = allCpEntries))

      //      val resolvedFiles = resolveViaProject(project, cpEntries, includeEntires)
      log.debug("Resolving project prerequisites took " + (System.currentTimeMillis - ts) + " milliseconds")
      resolved
    }

    val additionalClasspath = resolved.flatClasspath.map(_.getPath).toArray
    //    val pluginClasspath = resolvedFiles.plugins.map(_.getPath).toArray
    val includes = resolved.includes

    val addCompileCp: Array[String] = additionalClasspath ++ additionalProjectCompileClasspath

    // TODO: also check additional classpath entries 

    def compileWhenNecessary(checkLock: Boolean): String =
      checkInfoFileUpToDate(includes) match {
        case LastRunInfo(true, className, _) =>
          log.debug("Using previously compiled and up-to-date build class: " + className)
          className
        case LastRunInfo(_, _, reason) if !checkLock =>
          log.debug("Compilation of build script " + scriptFile + " is necessary. Reason: " + reason)
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

    useExistingCompiled(project, additionalProjectRuntimeClasspath, buildClassName, resolved.classpathTrees)
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

  protected def useExistingCompiled(project: Project, classpath: Array[String], className: String, classpathTrees: Seq[CpTree]): Any = {
    val start = System.currentTimeMillis
    log.debug("Loading compiled version of build script: " + scriptFile)

    val cl = ProjectClassLoader(
      project = project,
      classpathUrls = Array(targetDir.toURI.toURL) ++ classpath.map(cp => new File(cp).toURI.toURL),
      parent = getClass.getClassLoader,
      classpathTrees = classpathTrees)

    val clEnd = System.currentTimeMillis
    log.debug("Creating the project classloader took " + (clEnd - start) + " msec")

    val clazz: Class[_] = try cl.loadClass(className) catch {
      case e: ClassNotFoundException => throw new ProjectConfigurationException("Buildfile \"" + scriptFile + "\" does not contain a class \"" + className + "\".")
    }

    val ctr = clazz.getConstructor(classOf[Project])
    val scriptInstance = ctr.newInstance(project)
    // We assume, that everything is done in constructor, so we are done here
    project.finalizePlugins

    val end = System.currentTimeMillis - start
    log.debug("Finished loading of compiled version of build script: " + scriptFile + " after " + end + " msec")
    scriptInstance
  }

  def clean(): Unit = if (targetBaseDir.exists) {
    monitor.info(CmdlineMonitor.Verbose, "Deleting dir: " + targetBaseDir)
    targetBaseDir.deleteRecursive
  }

  def cleanScala(): Unit = if (targetDir.exists) {
    monitor.info(CmdlineMonitor.Verbose, "Deleting dir: " + targetDir)
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

        log.debug("Executing Scala Compiler with args: " + params.mkString(" "))
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

        log.debug("Executing Scala Compiler with args: " + params.mkString(" "))
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
          log.debug("Compilation with CompileClient failed. Trying non-distributed Scala compiler.")
          compileWithoutFsc
      }
    }
  }

}
