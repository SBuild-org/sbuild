package de.tototec.sbuild.runner

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FileWriter
import java.net.URL
import java.net.URLClassLoader
import scala.io.BufferedSource
import de.tototec.sbuild.HttpSchemeHandlerBase
import de.tototec.sbuild.OSGiVersion
import de.tototec.sbuild.Path
import de.tototec.sbuild.Project
import de.tototec.sbuild.ResolveResult
import de.tototec.sbuild.SBuildException
import de.tototec.sbuild.SBuildLogger
import de.tototec.sbuild.SBuildVersion
import de.tototec.sbuild.Util
import de.tototec.sbuild.LogLevel
import de.tototec.sbuild.ProjectConfigurationException
import de.tototec.sbuild.TargetRef
import de.tototec.sbuild.TargetNotFoundException
import de.tototec.sbuild.ProjectConfigurationException
import de.tototec.sbuild.ProjectConfigurationException
import de.tototec.sbuild.WithinTargetExecution
import de.tototec.sbuild.TargetContextImpl
import java.lang.reflect.Method
import de.tototec.sbuild.BuildFileProject
import scala.util.Try
import java.text.ParseException
import scala.annotation.tailrec
import de.tototec.sbuild.ExportDependencies
import de.tototec.sbuild.TargetRefs
import de.tototec.sbuild.execute.TargetExecutor
import de.tototec.sbuild.execute.InMemoryTransientTargetCache

object ProjectScript {

  val InfoFileName = "sbuild.info.xml"

  def cutSimpleComment(str: String): String = {
    var index = str.indexOf("//")
    while (index >= 0) {
      // found a comment candidate
      val substring = str.substring(0, index)
      if (substring.length > 0 && substring.endsWith("\\")) {
        // detected escaped comment, ignore this one
        index = str.indexOf("//", index + 1)
      } else {
        // check, that we were not inside of a string
        val DoubleQuote = """[^\\](\\)*"""".r
        val doubleQuoteCount = DoubleQuote.findAllIn(substring).size
        if ((doubleQuoteCount % 2) == 0) {
          // a real comment, remove this one
          return substring
        } else {
          // detected comment in quote
          index = str.indexOf("//", index + 1)
        }
      }
    }
    str
  }

  def unescapeStrings(str: String): String = {

    // http://www.java-blog-buch.de/0304-escape-sequenzen/
    @tailrec
    def unescape(seen: List[Char], str: List[Char]): List[Char] = str match {
      case '\\' :: xs => xs match {
        case '\\' :: ys => unescape('\\' :: seen, ys) // backslash
        case 'b' :: ys => unescape('\b' :: seen, ys) // backspace
        case 'n' :: ys => unescape('\n' :: seen, ys) // newline
        case 'r' :: ys => unescape('\r' :: seen, ys) // carriage return
        case 't' :: ys => unescape('\t' :: seen, ys) // tab
        case 'f' :: ys => unescape('\f' :: seen, ys) // formfeed
        case ''' :: ys => unescape('\'' :: seen, ys) // single quote
        case '"' :: ys => unescape('\"' :: seen, ys) // double quote
        case 'u' :: a :: b :: c :: d :: ys => unescape(Seq(a, b, c, d).toString.toInt.toChar :: seen, ys) // unicode
        case a :: b :: c :: ys if a.isDigit && b.isDigit && c.isDigit && Seq(a, b, c).toString.toInt <= 377 =>
          unescape(Integer.parseInt(Seq(a, b, c).toString, 8).toChar :: seen, ys) // octal with 3 digits
        case a :: b :: ys if a.isDigit && b.isDigit =>
          unescape(Integer.parseInt(Seq(a, b).toString, 8).toChar :: seen, ys)
        case a :: ys if a.isDigit =>
          unescape(Integer.parseInt(a.toString, 8).toChar :: seen, ys)
        case a :: _ => throw new ParseException(s"""Cannot parse escape sequence "\\$a".""", -1) // error
        case Nil => throw new ParseException("""Cannot parse unclosed escape sequence at end of string.""", -1) // error
      }
      case Nil => seen
      case x :: xs => unescape(x :: seen, xs)
    }

    unescape(Nil, str.toList).reverse.mkString
  }

  /**
   * Drop all caches. For now, this is the Scalac compiler and its ClassLoader.
   */
  def dropCaches { scalaCompilerAndClassloader = None }
  /**
   * Cached instance of the Scalac compiler class and its ClassLoader.
   * Using a cache will provide the benefit, that loading is faster and we potentially profit from any JIT-compilation at runtime.
   */
  private var scalaCompilerAndClassloader: Option[(Any, Method, Method)] = None

}

class ProjectScript(_scriptFile: File,
                    sbuildClasspath: Array[String],
                    compileClasspath: Array[String],
                    additionalProjectClasspath: Array[String],
                    compilerPluginJars: Array[String],
                    noFsc: Boolean,
                    log: SBuildLogger) {

  import ProjectScript._

  def this(scriptFile: File,
           classpathConfig: ClasspathConfig,
           log: SBuildLogger) {
    this(scriptFile,
      classpathConfig.sbuildClasspath,
      classpathConfig.compileClasspath,
      classpathConfig.projectClasspath,
      classpathConfig.compilerPluginJars,
      classpathConfig.noFsc,
      log)
  }

  private[this] val scriptFile: File = Path.normalize(_scriptFile)
  require(scriptFile.isFile, "scriptFile must be a file")
  private[this] val projectDir: File = scriptFile.getParentFile

  private[this] val buildTargetDir = ".sbuild";
  private[this] val buildFileTargetDir = ".sbuild/scala/" + scriptFile.getName;

  private[this] val scriptBaseName = scriptFile.getName.endsWith(".scala") match {
    case true => scriptFile.getName.substring(0, scriptFile.getName.length - 6)
    case false =>
      log.log(LogLevel.Debug, "Scriptfile name does not end in '.scala'")
      scriptFile.getName
  }
  private[this] lazy val targetBaseDir: File = new File(scriptFile.getParentFile, buildTargetDir)
  private[this] lazy val targetDir: File = new File(scriptFile.getParentFile, buildFileTargetDir)
  private[this] val defaultTargetClassName = scriptBaseName
  private[this] def targetClassFile(targetClassName: String): File = new File(targetDir, targetClassName + ".class")
  // lazy val targetClassFile = new File(targetDir, scriptBaseName + ".class")
  private[this] lazy val infoFile = new File(targetDir, InfoFileName)

  /** File that contains the map from Scala type to the containing source file. */
  val typesToIncludedFilesPropertiesFile: File = new File(targetDir, "analyzedtypes.properties")

  private[this] def checkFile = if (!scriptFile.exists) {
    throw new RuntimeException(s"Could not find build file: ${scriptFile.getName}\n" +
      s"Searched in: ${scriptFile.getAbsoluteFile.getParent}")
  }

  /**
   * Compile this project script (if necessary) and apply it to the given Project.
   */
  def compileAndExecute(project: Project): Any = {
    checkFile

    val version = readAnnotationWithSingleAttribute("version", "value")
    val osgiVersion = OSGiVersion.parseVersion(version)
    if (osgiVersion.compareTo(new OSGiVersion(SBuildVersion.osgiVersion)) > 0) {
      throw new SBuildException("The buildscript '" + scriptFile + "' requires at least SBuild version: " + version)
    }

    val addCp: Array[String] = readAdditionalClasspath(project) ++ additionalProjectClasspath

    val includes: Map[String, Seq[File]] = readIncludeFiles(project)

    // TODO: also check additional classpath entries 
    val buildClassName = checkInfoFileUpToDate(includes) match {
      case LastRunInfo(true, className, _) => className
      case LastRunInfo(_, _, reason) =>
        // println("Compiling build script " + scriptFile + "...")
        newCompile(sbuildClasspath ++ addCp, includes, reason)
    }

    // Experimental: Attach included files 
    {
      implicit val _p = project
      val includedFiles = includes.flatMap { case (k, v) => v }.map(TargetRef(_)).toSeq
      ExportDependencies("sbuild.project.includes", TargetRefs.fromSeq(includedFiles))
    }

    useExistingCompiled(project, addCp, buildClassName)
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
          log.log(LogLevel.Debug, "Could not evaluate up-to-date state of included files.", e)
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

  def readAnnotationWithVarargAttribute(annoName: String, valueName: String, singleArg: Boolean = false): Array[String] = {
    var inAnno = false
    var skipRest = false
    val it = new BufferedSource(new FileInputStream(scriptFile)).getLines()
    var annoLine = ""
    while (!skipRest && it.hasNext) {
      var line = cutSimpleComment(it.next).trim

      if (inAnno) {
        if (line.endsWith(")")) {
          skipRest = true
          line = line.substring(0, line.length - 1).trim
        }
        annoLine = annoLine + " " + line
      }
      if (line.startsWith("@" + annoName + "(")) {
        line = line.substring(annoName.length + 2).trim
        if (line.endsWith(")")) {
          line = line.substring(0, line.length - 1).trim
          skipRest = true
        }
        inAnno = true
        annoLine = line
      }
    }

    annoLine = annoLine.trim

    if (annoLine.length > 0) {
      if (annoLine.startsWith(valueName)) {
        annoLine = annoLine.substring(valueName.length).trim
        if (annoLine.startsWith("=")) {
          annoLine = annoLine.substring(1).trim
        } else {
          throw new RuntimeException("Expected a '=' sign but got a '" + annoLine(0) + "'")
        }
      }

      val annoItems =
        if (singleArg) Array(annoLine)
        else
          annoLine.split(",")

      // TODO: also support triple-quotes
      val finalAnnoItems = annoItems map { item => item.trim } map { item =>
        if (item.startsWith("\"") && item.endsWith("\"")) {
          unescapeStrings(item.substring(1, item.length - 1))
        } else {
          throw new RuntimeException("Unexpection token found: " + item)
        }
      }

      finalAnnoItems
    } else {
      Array()
    }
  }

  protected def readAnnotationWithSingleAttribute(annoName: String, valueName: String): String = {
    readAnnotationWithVarargAttribute(annoName, valueName, true) match {
      case Array() => ""
      case Array(value) => value
      case _ => throw new RuntimeException("Unexpected annotation syntax detected. Expected single arg annotation @" + annoName)
    }
  }

  protected def readIncludeFiles(project: Project): Map[String, Seq[File]] = {
    log.log(LogLevel.Debug, "About to find include files.")
    val cp = readAnnotationWithVarargAttribute(annoName = "include", valueName = "value")
    log.log(LogLevel.Debug, "Using include files: " + cp.mkString(", "))

    // TODO: specific error message, when fetch or download fails
    resolveViaProject(cp, project, "@include entry")
  }

  protected def resolveViaProject(targets: Seq[String], project: Project, purposeOfEntry: String): Map[String, Seq[File]] =
    targets.map(t => (t -> resolveViaProject(t, project, purposeOfEntry))).toMap

  protected def resolveViaProject(target: String, project: Project, purposeOfEntry: String): Seq[File] = {

    class RequirementsResolver extends BuildFileProject(
      _projectFile = project.projectFile,
      log = new SBuildLogger {
        override def log(logLevel: LogLevel, msg: => String, cause: Throwable = null) {
          //          val level = logLevel match {
          //            case LogLevel.Info => LogLevel.Debug
          //            case x => x
          //          }
          project.log.log(logLevel, "RequirementsResolver: " + msg, cause)
        }
      }
    )
    implicit val p: Project = new RequirementsResolver

    p.determineRequestedTarget(targetRef = TargetRef(target), searchInAllProjects = true, supportCamelCaseShortCuts = false) match {

      case None =>
        // not found
        // if an existing file, then proceed.
        val targetRef = TargetRef.fromString(target)
        targetRef.explicitProto match {
          case None | Some("file") if targetRef.explicitProject == None && Path(targetRef.nameWithoutProto).exists =>
            return Seq(Path(targetRef.nameWithoutProto))
          case _ =>
            throw new TargetNotFoundException(s"""Could not found ${purposeOfEntry} "${target}" in project ${scriptFile}.""")
        }

      case Some(target) =>

        val targetExecutor = new TargetExecutor(
          baseProject = project,
          log = project.log,
          logConfig = TargetExecutor.LogConfig(
              executing = LogLevel.Debug,
              topLevelSkipped = LogLevel.Debug
          )
        )

        // TODO: progress
        val executedTarget =
          targetExecutor.preorderedDependenciesTree(
            curTarget = target,
            transientTargetCache = Some(new InMemoryTransientTargetCache())
          )
        executedTarget.targetContext.targetFiles
    }
  }

  protected def readAdditionalClasspath(project: Project): Array[String] = {
    log.log(LogLevel.Debug, "About to find additional classpath entries.")
    val cp = readAnnotationWithVarargAttribute(annoName = "classpath", valueName = "value")
    log.log(LogLevel.Debug, "Using additional classpath entries: " + cp.mkString(", "))
    resolveViaProject(cp, project, "@classpath entry").flatMap { case (key, value) => value }.map { _.getPath }.toArray
  }

  protected def useExistingCompiled(project: Project, classpath: Array[String], className: String): Any = {
    log.log(LogLevel.Debug, "Loading compiled version of build script: " + scriptFile)
    val cl = new URLClassLoader(Array(targetDir.toURI.toURL) ++ classpath.map(cp => new File(cp).toURI.toURL), getClass.getClassLoader)
    log.log(LogLevel.Debug, "CLassLoader loads build script from URLs: " + cl.asInstanceOf[{ def getURLs: Array[URL] }].getURLs.mkString(", "))
    val clazz: Class[_] = cl.loadClass(className)
    val ctr = clazz.getConstructor(classOf[Project])
    val scriptInstance = ctr.newInstance(project)
    // We assume, that everything is done in constructor, so we are done here
    project.applyPlugins
    scriptInstance
  }

  def clean() {
    Util.delete(targetBaseDir)
  }
  def cleanScala() {
    Util.delete(targetDir)
  }

  protected def newCompile(classpath: Array[String], includes: Map[String, Seq[File]], printReason: Option[String] = None): String = {
    cleanScala()
    targetDir.mkdirs
    log.log(LogLevel.Info,
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

    log.log(LogLevel.Debug, "Writing info file: " + infoFile)
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
                         log.log(LogLevel.Debug, s"""@Include "${key}" resolved to ${value.size} files: ${value}""")
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
      log.log(LogLevel.Debug, "Using additional classpath for scala compiler: " + compileClasspath.mkString(", "))
      new URLClassLoader(compileClasspath.map { f => new File(f).toURI.toURL }, getClass.getClassLoader)
    }

    def compileWithFsc {
      val compileClient = lazyCompilerClassloader.loadClass("scala.tools.nsc.StandardCompileClient").newInstance
      //      import scala.tools.nsc.StandardCompileClient
      //      val compileClient = new StandardCompileClient
      val compileMethod = compileClient.asInstanceOf[Object].getClass.getMethod("process", Array(classOf[Array[String]]): _*)
      log.log(LogLevel.Debug, "Executing CompileClient with args: " + params.mkString(" "))
      val retVal = compileMethod.invoke(compileClient, params).asInstanceOf[Boolean]
      if (!retVal) throw new SBuildException("Could not compile build file " + scriptFile.getAbsolutePath + " with CompileClient. See compiler output.")
    }

    def compileWithoutFsc {
      val (compiler, compilerMethod, reporterMethod) = ProjectScript.scalaCompilerAndClassloader match {
        case Some((c, cm, rm)) =>
          log.log(LogLevel.Debug, "Reusing cached compiler instance.")
          (c, cm, rm)
        case None =>
          val compiler = lazyCompilerClassloader.loadClass("scala.tools.nsc.Main")
          val compilerMethod = compiler.getMethod("process", Array(classOf[Array[String]]): _*)
          val reporterMethod = compiler.getMethod("reporter")
          val cache = (compiler, compilerMethod, reporterMethod)
          log.log(LogLevel.Debug, "Caching compiler for later use.")
          ProjectScript.scalaCompilerAndClassloader = Some(cache)
          cache
      }

      log.log(LogLevel.Debug, "Executing Scala Compile with args: " + params.mkString(" "))
      compilerMethod.invoke(null, params)
      val reporter = reporterMethod.invoke(null)
      val hasErrorsMethod = reporter.asInstanceOf[Object].getClass.getMethod("hasErrors")
      val hasErrors = hasErrorsMethod.invoke(reporter).asInstanceOf[Boolean]
      if (hasErrors) throw new SBuildException("Could not compile build file " + scriptFile.getAbsolutePath + " with scala compiler. See compiler output.")
    }

    if (noFsc) {
      compileWithoutFsc
    } else {
      try {
        compileWithFsc
      } catch {
        case e: SBuildException => throw e
        case e: Exception =>
          log.log(LogLevel.Debug, "Compilation with CompileClient failed. trying non-dispributed Scala compiler.")
          // throw new SBuildException("Could not compile build file " + scriptFile.getName + " with CompileClient. Exception: " + e.getMessage, e)
          // we should try with normal scala compiler
          compileWithoutFsc
      }
    }
  }

}
