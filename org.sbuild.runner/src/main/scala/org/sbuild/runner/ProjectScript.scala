package org.sbuild.runner

import java.io.File
import java.io.FileInputStream
import scala.io.BufferedSource
import org.sbuild.CmdlineMonitor
import org.sbuild.Logger
import org.sbuild.Path
import org.sbuild.Project
import org.sbuild.ProjectConfigurationException
import org.sbuild.internal.I18n
import org.sbuild.SBuildVersion
import org.sbuild.BuildfileCompilationException
import org.sbuild.toRichFile
import java.io.FileWriter
import java.net.URLClassLoader
import org.sbuild.SBuildException
import java.lang.reflect.Method
import org.sbuild.internal.Bootstrapper

object ProjectScript {
  val InfoFileName = "sbuild.info.xml"

  private[this] val log = Logger[ProjectScript]

  case class ScriptEnv(
      scriptFile: File,
      sbuildWorkDir: File,
      sourceStream: Stream[String]) {

    require(scriptFile.isFile())

    val baseDir: File = scriptFile.getParentFile()

    val classesDir: File = new File(sbuildWorkDir, s"scala/${scriptFile.getName()}")

    val scriptBaseName: String = scriptFile.getName.endsWith(".scala") match {
      case true => scriptFile.getName.substring(0, scriptFile.getName.length - 6)
      case false =>
        log.debug("Scriptfile name does not end in '.scala'")
        scriptFile.getName
    }

    def scriptIterator: Iterator[String] = sourceStream.iterator

    val lockFile = new File(sbuildWorkDir, "lock/" + scriptFile.getName + ".lock")

    def targetClassFile(targetClassName: String): File = new File(classesDir, targetClassName + ".class")

    val infoFile = new File(classesDir, InfoFileName)

    val typesToIncludedFilesPropertiesFile: File = new File(classesDir, "analyzedtypes.properties")

  }

  // TODO: add some classpath to compile against this script
  case class LoadedScriptClass(
      scriptEnv: ScriptEnv,
      scriptClass: Class[_],
      projectClassLoader: ProjectClassLoader,
      bootstrapClass: Option[LoadedScriptClass],
      scriptDefinedClasspath: Seq[File]) {

    def applyToProject(project: Project): Unit = {
      bootstrapClass.map(_.applyToProject(project))

      projectClassLoader.registerToProject(project)

      val ctr = scriptClass.getConstructor(classOf[Project])
      val scriptInstance = ctr.newInstance(project)
    }

  }

  case class LastRunInfo(upToDate: Boolean, targetClassName: String, issues: Option[String] = None)

}

class ProjectScript(classpaths: Classpaths, fileLocker: FileLocker, noFsc: Boolean) {
  import ProjectScript._

  private[this] val log = Logger[ProjectScript]
  private[this] val i18n = I18n[ProjectScript]
  import i18n._

  val annotationReader = new AnnotationReader()

  case class CachedScalaCompiler(compilerClass: Class[_], compilerMethod: Method, reporterMethod: Method)
  case class CachedExtendedScalaCompiler(compilerClass: Class[_], compilerMethod: Method, reporterMethod: Method, outputMethod: Method, clearOutputMethod: Method)
  /**
   * Cached instance of the Scalac compiler class and its ClassLoader.
   * Using a cache will provide the benefit, that loading is faster and we potentially profit from any JIT-compilation at runtime.
   */
  private[this] var cachedScalaCompiler: Option[CachedScalaCompiler] = None
  private[this] var cachedExtendedScalaCompiler: Option[CachedExtendedScalaCompiler] = None
  private[this] var cachedScriptClasses: Map[String, LoadedScriptClass] = Map()

  /**
   * Drop all caches. For now, this is the Scalac compiler and its ClassLoader.
   */
  def dropCaches() {
    cachedScalaCompiler = None
    cachedExtendedScalaCompiler = None
    cachedScriptClasses = Map()
  }

  def loadScriptClass(scriptFile: File, monitor: CmdlineMonitor): LoadedScriptClass = {
    val normalizedScriptFile = Path.normalize(scriptFile).getPath
    // TODO: thread safety?
    cachedScriptClasses.get(normalizedScriptFile) match {
      case Some(loadedScriptClass) => loadedScriptClass
      case None =>

        val scriptEnv = checkScriptFile(scriptFile)
        val version = readAndCheckAnnoVersion(scriptEnv)

        val bootstrapClass = readAnnoBootstrap(scriptEnv) match {
          case Some(bootstrapFile) =>
            Some(loadScriptClass(Path.normalize(new File(bootstrapFile), baseDir = scriptEnv.baseDir), monitor))
          case None =>
            // TODO: load built-in defaults: none for now
            None
        }

        val includeEntries = readAnnoInclude(scriptEnv)
        val classpathEntries = readAnnoClasspath(scriptEnv)

        // resolve @includes + @classpath
        val resolved = {
          val ts = System.currentTimeMillis

          val bootstrappers = Seq(new Bootstrapper {
            override def applyToProject(project: Project): Unit = {
              bootstrapClass.map(_.applyToProject(project))
            }
          })
          val classpathResolver = new ClasspathResolver(scriptEnv.scriptFile, monitor.mode, bootstrappers)
          val resolved = classpathResolver.apply(ClasspathResolver.ResolveRequest(includeEntries = includeEntries, classpathEntries = classpathEntries))
          log.debug("Resolving project prerequisites took " + (System.currentTimeMillis - ts) + " milliseconds")
          resolved
        }

        val scriptDefinedClasspath: Seq[File] =
          bootstrapClass.map(_.scriptDefinedClasspath).getOrElse(Seq()) ++
            resolved.flatClasspath

        log.debug("Using script defined classpath (boot + this): " + scriptDefinedClasspath.mkString("\n  ", "\n  ", ""))

        val className = compileScript(
          scriptEnv = scriptEnv,
          bootstrapClasspath = scriptDefinedClasspath,
          includes = resolved.includes,
          classpath = classpaths.sbuildClasspath.toSeq ++ classpaths.projectCompileClasspath.toSeq ++ scriptDefinedClasspath.map(_.getPath),
          monitor = monitor
        )

        // load Script Class
        val loadedScriptClass = {
          log.debug("Loading compiled version of build script: " + scriptEnv.scriptFile)

          // TODO: load all extra classes only in top-most bootstrapper project classloader
          val classpath = classpaths.projectRuntimeClasspath
          val parentClassLoader = bootstrapClass match {
            case Some(bc) => bc.projectClassLoader
            case None => getClass().getClassLoader()
          }
          val classpathTrees = resolved.classpathTrees

          val cl = new ProjectClassLoader(
            classpathUrls = Array(scriptEnv.classesDir.toURI.toURL) ++ classpath.map(cp => new File(cp).toURI.toURL),
            parent = parentClassLoader,
            classpathTrees = classpathTrees)

          val clazz: Class[_] = try cl.loadClass(className) catch {
            case e: ClassNotFoundException => throw new ProjectConfigurationException("Buildfile \"" + scriptFile + "\" does not contain a class \"" + className + "\".")
          }

          LoadedScriptClass(
            scriptEnv = scriptEnv,
            scriptClass = clazz,
            projectClassLoader = cl,
            bootstrapClass = bootstrapClass,
            scriptDefinedClasspath = scriptDefinedClasspath
          )
        }

        // return
        cachedScriptClasses += normalizedScriptFile -> loadedScriptClass
        loadedScriptClass
    }
  }

  def checkScriptFile(scriptFile: File): ScriptEnv = {
    val file = Path.normalize(scriptFile)

    if (!file.exists || !file.isFile) {
      val msg = preparetr("Project buildfile \"{0}\" does not exists or is not a file.", scriptFile)
      val ex = new ProjectConfigurationException(msg.notr, null, msg.tr)
      ex.buildScript = Some(file)
      throw ex
    }

    ScriptEnv(
      scriptFile = file,
      sourceStream = new BufferedSource(new FileInputStream(scriptFile)).getLines().toStream,
      sbuildWorkDir = new File(file.getParentFile(), ".sbuild")
    )
  }

  def readAndCheckAnnoVersion(scriptEnv: ScriptEnv): String = {
    val versionOption = annotationReader.findFirstAnnotationSingleValue(scriptEnv.scriptIterator, "version", "value")
    val version = versionOption.getOrElse("")
    new VersionChecker().assertBuildscriptVersion(version, scriptEnv.scriptFile)
    version
  }

  def readAnnoBootstrap(scriptEnv: ScriptEnv): Option[String] = {
    annotationReader.findFirstAnnotationSingleValue(buildScript = scriptEnv.scriptIterator, annoName = "bootstrap", valueName = "value")
  }

  def readAnnoInclude(scriptEnv: ScriptEnv): Seq[String] = {
    log.debug("About to find include files for: " + scriptEnv.scriptFile)
    val includeEntires = annotationReader.
      findFirstAnnotationWithVarArgValue(scriptEnv.scriptIterator, annoName = "include", varArgValueName = "value").
      map(_.values).getOrElse(Array())
    if (!includeEntires.isEmpty) {
      log.debug(scriptEnv.scriptFile + " contains @include annotation: " + includeEntires.mkString("@include(", ",\n  ", ")"))
    }
    includeEntires
  }

  def readAnnoClasspath(scriptEnv: ScriptEnv): Seq[String] = {
    log.debug("About to find additional classpath entries for: " + scriptEnv.scriptFile)
    val allCpEntries = annotationReader.
      findFirstAnnotationWithVarArgValue(scriptEnv.scriptIterator, annoName = "classpath", varArgValueName = "value").
      map(_.values).getOrElse(Array())
    if (!allCpEntries.isEmpty) {
      log.debug(scriptEnv.scriptFile + " contains @classpath annotation: " + allCpEntries.mkString("@classpath(", ",\n  ", ")"))
    }
    allCpEntries
  }

  def compileScript(scriptEnv: ScriptEnv, bootstrapClasspath: Seq[File], includes: Map[String, Seq[File]], classpath: Seq[String], monitor: CmdlineMonitor): String = {
    val scriptFile = scriptEnv.scriptFile
    val lockFile = scriptEnv.lockFile

    def compileWhenNecessary(checkLock: Boolean): String =
      checkInfoFileUpToDate(scriptEnv, bootstrapClasspath, includes) match {
        case LastRunInfo(true, className, _) =>
          log.debug("Using previously compiled and up-to-date build class: " + className)
          className
        case LastRunInfo(_, _, reason) if !checkLock =>
          log.debug("Compiling build script " + scriptFile + " is necessary. Reason: " + reason)
          newCompile(scriptEnv, classpath, bootstrapClasspath, includes, reason, monitor)
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
    buildClassName
  }

  def checkInfoFileUpToDate(scriptEnv: ScriptEnv, bootstrapClasspath: Seq[File], includes: Map[String, Seq[File]]): LastRunInfo = {
    val scriptFile = scriptEnv.scriptFile
    val infoFile = scriptEnv.infoFile
    val defaultTargetClassName = scriptEnv.scriptBaseName

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

      val classFile = scriptEnv.targetClassFile(targetClassName)
      val scriptFileUpToDate = scriptEnv.scriptFile.length == sourceSize &&
        scriptFile.lastModified == sourceLastModified &&
        classFile.lastModified == targetClassLastModified &&
        classFile.lastModified >= scriptFile.lastModified

      lazy val bootstrapClasspathMatch: Boolean = try {
        val lastBoot = (info \ "bootstrapClasspath" \ "bootstrap").map { lastInclude =>
          ((lastInclude \ "path").text, (lastInclude \ "lastModified").text.toLong)
        }.toMap

        bootstrapClasspath.size == lastBoot.size &&
          bootstrapClasspath.forall { file =>
            lastBoot.get(file.getPath()) match {
              case Some(time) => file.lastModified == time
              case _ => false
            }
          }
      } catch {
        case e: Exception =>
          log.debug("Could not evaluate up-to-date state of bootstrap classpath.", e)
          false
      }

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
        upToDate = sbuildVersionMatch && scriptFileUpToDate && bootstrapClasspathMatch && includesMatch,
        targetClassName = targetClassName,
        issues = (sbuildVersionMatch, scriptFileUpToDate, bootstrapClasspathMatch, includesMatch) match {
          case (false, _, _, _) => Some(s"SBuild version changed (${sbuildVersion} -> ${SBuildVersion.version})")
          case (_, false, _, _) => None
          case (_, _, false, _) => Some("Bootstrap changed")
          case (_, _, _, false) => Some("Includes changed")
          case _ => None
        }
      )
    }
  }

  def cleanScala(scriptEnv: ScriptEnv, monitor: CmdlineMonitor): Unit =
    if (scriptEnv.classesDir.exists) {
      monitor.info(CmdlineMonitor.Verbose, "Deleting dir: " + scriptEnv.classesDir)
      scriptEnv.classesDir.deleteRecursive
    }

  protected def newCompile(scriptEnv: ScriptEnv, classpath: Seq[String], bootstrapCp: Seq[File], includes: Map[String, Seq[File]], printReason: Option[String] = None, monitor: CmdlineMonitor): String = {
    val scriptFile = scriptEnv.scriptFile

    cleanScala(scriptEnv, monitor)
    scriptEnv.classesDir.mkdirs
    monitor.info(CmdlineMonitor.Default,
      (printReason match {
        case None => ""
        case Some(r) => r + ": "
      }) + "Compiling build script: " + scriptFile +
        (if (includes.isEmpty) "" else " and " + includes.size + " included files") +
        "..."
    )

    compile(scriptEnv, classpath.mkString(File.pathSeparator), includes, monitor)

    val (realTargetClassName, realTargetClassFile) = scriptEnv.targetClassFile(scriptEnv.scriptBaseName) match {
      case classExists if classExists.exists() => (scriptEnv.scriptBaseName, classExists)
      case _ => ("SBuild", scriptEnv.targetClassFile("SBuild"))
    }

    log.debug("Writing info file: " + scriptEnv.infoFile)
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
                 <bootstrapClasspath>
                   {
                     bootstrapCp.map {
                       case file =>
                         <bootstrap>
                           <path>{ file.getPath }</path>
                           <lastModified>{ file.lastModified }</lastModified>
                         </bootstrap>
                     }

                   }
                 </bootstrapClasspath>
               </sbuild>
    val file = new FileWriter(scriptEnv.infoFile)
    xml.XML.write(file, info, "UTF-8", true, null)
    file.close

    realTargetClassName
  }

  protected def compile(scriptEnv: ScriptEnv, classpath: String, includes: Map[String, Seq[File]], monitor: CmdlineMonitor) {
    val compilerPluginSettings = classpaths.compilerPluginJars match {
      case Array() => Array[String]()
      case jars => jars.map { jar: String => "-Xplugin:" + jar }
    }
    val params = compilerPluginSettings ++ Array(
      "-P:analyzetypes:outfile=" + scriptEnv.typesToIncludedFilesPropertiesFile.getPath(),
      "-classpath", classpath,
      "-deprecation",
      "-g:vars",
      "-d", scriptEnv.classesDir.getPath,
      scriptEnv.scriptFile.getPath) ++
      (includes.flatMap { case (name, files) => files }.map { _.getPath })

    lazy val lazyCompilerClassloader = {
      log.debug("Using additional classpath for scala compiler: " + classpaths.compileClasspath.mkString(", "))
      new URLClassLoader(classpaths.compileClasspath.map { f => new File(f).toURI.toURL }, getClass.getClassLoader)
    }

    def compileWithFsc {
      val compileClient = lazyCompilerClassloader.loadClass("scala.tools.nsc.StandardCompileClient").newInstance
      //      import scala.tools.nsc.StandardCompileClient
      //      val compileClient = new StandardCompileClient
      val compileMethod = compileClient.asInstanceOf[Object].getClass.getMethod("process", Array(classOf[Array[String]]): _*)
      log.debug("Executing CompileClient with args: " + params.mkString(" "))
      val retVal = compileMethod.invoke(compileClient, params).asInstanceOf[Boolean]
      if (!retVal) throw new BuildfileCompilationException("Could not compile build file " + scriptEnv.scriptFile.getAbsolutePath + " with CompileClient. See compiler output.")
    }

    def compileWithoutFsc {

      val useExtendedCompiler = true
      if (useExtendedCompiler) {

        val cachedCompiler = cachedExtendedScalaCompiler match {
          case Some(cached) =>
            log.debug("Reusing cached extended compiler instance.")
            cached
          case None =>
            val compiler = lazyCompilerClassloader.loadClass("org.sbuild.scriptcompiler.ScriptCompiler")
            //            val compiler = compilerClass.getConstructor().newInstance()
            val compilerMethod = compiler.getMethod("process", Array(classOf[Array[String]]): _*)
            val reporterMethod = compiler.getMethod("reporter")
            val outputMethod = compiler.getMethod("getRecordedOutput")
            val clearOutputMethod = compiler.getMethod("clearRecordedOutput")
            val cache = CachedExtendedScalaCompiler(compiler, compilerMethod, reporterMethod, outputMethod, clearOutputMethod)
            log.debug("Caching extended compiler for later use.")
            cachedExtendedScalaCompiler = Some(cache)
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
          throw new BuildfileCompilationException("Could not compile build file " + scriptEnv.scriptFile.getAbsolutePath + " with scala compiler.\nCompiler output:\n" + output.mkString("\n"))
        }
        cachedCompiler.clearOutputMethod.invoke(compilerInstance)

      } else {
        val cachedCompiler = cachedScalaCompiler match {
          case Some(cached) =>
            log.debug("Reusing cached compiler instance.")
            cached
          case None =>
            val compiler = lazyCompilerClassloader.loadClass("scala.tools.nsc.Main")
            val compilerMethod = compiler.getMethod("process", Array(classOf[Array[String]]): _*)
            val reporterMethod = compiler.getMethod("reporter")
            val cache = CachedScalaCompiler(compiler, compilerMethod, reporterMethod)
            log.debug("Caching compiler for later use.")
            cachedScalaCompiler = Some(cache)
            cache
        }

        log.debug("Executing Scala Compile with args: " + params.mkString(" "))
        cachedCompiler.compilerMethod.invoke(null, params)
        val reporter = cachedCompiler.reporterMethod.invoke(null)
        val hasErrors = reporter.asInstanceOf[{ def hasErrors(): Boolean }].hasErrors
        if (hasErrors) throw new BuildfileCompilationException("Could not compile build file " + scriptEnv.scriptFile.getAbsolutePath + " with scala compiler. See compiler output.")
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