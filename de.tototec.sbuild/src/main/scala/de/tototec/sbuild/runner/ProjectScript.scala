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

object ProjectScript {
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
}

class ProjectScript(_scriptFile: File,
                    sbuildClasspath: Array[String],
                    compileClasspath: Array[String],
                    additionalProjectClasspath: Array[String],
                    noFsc: Boolean,
                    downloadCache: Option[DownloadCache],
                    log: SBuildLogger) {

  import ProjectScript._

  def this(scriptFile: File,
           classpathConfig: ClasspathConfig,
           downloadCache: Option[DownloadCache],
           log: SBuildLogger) {
    this(scriptFile,
      classpathConfig.sbuildClasspath,
      classpathConfig.compileClasspath,
      classpathConfig.projectClasspath,
      classpathConfig.noFsc,
      downloadCache,
      log)
  }

  val scriptFile: File = Path.normalize(_scriptFile)
  require(scriptFile.isFile, "scriptFile must be a file")
  val projectDir: File = scriptFile.getParentFile

  val buildTargetDir = ".sbuild";
  val buildFileTargetDir = ".sbuild/scala/" + scriptFile.getName;

  val scriptBaseName = scriptFile.getName.endsWith(".scala") match {
    case true => scriptFile.getName.substring(0, scriptFile.getName.length - 6)
    case false =>
      log.log(LogLevel.Debug, "Scriptfile name does not end in '.scala'")
      scriptFile.getName
  }
  lazy val targetBaseDir: File = new File(scriptFile.getParentFile, buildTargetDir)
  lazy val targetDir: File = new File(scriptFile.getParentFile, buildFileTargetDir)
  lazy val targetClassFile = new File(targetDir, scriptBaseName + ".class")
  lazy val infoFile = new File(targetDir, "sbuild.info.xml")

  def checkFile() = if (!scriptFile.exists) {
    throw new RuntimeException("Could not find build file: " + scriptFile.getName + "\nSearched in: "
      + scriptFile.getAbsoluteFile.getParent)
  }

  def compileAndExecute(project: Project): Any = {
    checkFile

    val infoFile = new File(targetDir, "sbuild.info.xml")

    val version = readAnnotationWithSingleAttribute("version", "value")
    val osgiVersion = OSGiVersion.parseVersion(version)
    if (osgiVersion.compareTo(new OSGiVersion(SBuildVersion.osgiVersion)) > 0) {
      throw new SBuildException("The buildscript '" + scriptFile + "' requires at least SBuild version: " + version)
    }

    val addCp: Array[String] = readAdditionalClasspath(project) ++ additionalProjectClasspath

    val includes: Array[File] = readIncludeFiles(project)

    if (!checkInfoFileUpToDate(includes)) {
      //      println("Compiling build script " + scriptFile + "...")
      newCompile(sbuildClasspath ++ addCp, includes)
    }

    useExistingCompiled(project, addCp)
  }

  def checkInfoFileUpToDate(includes: Array[File]): Boolean = {
    infoFile.exists && {
      val info = xml.XML.loadFile(infoFile)

      val sourceSize = (info \ "sourceSize").text.toLong
      val sourceLastModified = (info \ "sourceLastModified").text.toLong
      val targetClassLastModified = (info \ "targetClassLastModified").text.toLong
      val sbuildVersion = (info \ "sbuildVersion").text
      val sbuildOsgiVersion = (info \ "sbuildOsgiVersion").text

      def includesMatch: Boolean = try {
        val lastIncludes = (info \ "includes" \ "include").map { lastInclude =>
          ((lastInclude \ "path").text, (lastInclude \ "lastModified").text.toLong)
        }.toMap

        includes.length == lastIncludes.size &&
          includes.forall { include =>
            lastIncludes.get(include.getPath) match {
              case Some(time) => include.lastModified == time
              case _ => false
            }
          }
      } catch {
        case e =>
          log.log(LogLevel.Debug, "Could not evaluate up-to-date state of included files.", e)
          false
      }

      scriptFile.length == sourceSize &&
        scriptFile.lastModified == sourceLastModified &&
        targetClassFile.lastModified == targetClassLastModified &&
        sbuildVersion == SBuildVersion.version &&
        sbuildOsgiVersion == SBuildVersion.osgiVersion &&
        includesMatch
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

      val finalAnnoItems = annoItems map { item => item.trim } map { item =>
        if (item.startsWith("\"") && item.endsWith("\"")) {
          item.substring(1, item.length - 1)
        } else {
          throw new RuntimeException("Unexpection token found: " + item)
        }
      }

      finalAnnoItems
    } else {
      Array()
    }
  }

  def readAnnotationWithSingleAttribute(annoName: String, valueName: String): String = {
    readAnnotationWithVarargAttribute(annoName, valueName, true) match {
      case Array() => ""
      case Array(value) => value
      case _ => throw new RuntimeException("Unexpected annotation syntax detected. Expected single arg annotation @" + annoName)
    }
  }

  def readIncludeFiles(project: Project): Array[File] = {
    log.log(LogLevel.Debug, "About to find include files.")
    val cp = readAnnotationWithVarargAttribute(annoName = "include", valueName = "value")
    log.log(LogLevel.Debug, "Using include files: " + cp.mkString(", "))

    // TODO: specific error message, when fetch or download fails
    resolveViaProject(cp, project, "@include entry")

    //    cp.map { entry =>
    //      val fileEntry = Path.normalize(new File(entry), projectDir)
    //      if (!fileEntry.exists) {
    //        val ex = new ProjectConfigurationException("Could not found include file: " + entry)
    //        ex.buildScript = Some(scriptFile)
    //        throw ex
    //      }
    //      fileEntry
    //    }.distinct
  }

  def resolveViaProject(targets: Array[String], project: Project, purposeOfEntry: String): Array[File] =
    targets.flatMap { cpEntry =>
      val entryRef = TargetRef(cpEntry)(project)
      val files = try {
        entryRef.files.toArray
      } catch {
        case e: Exception =>
          val ex = new ProjectConfigurationException(s"""Could not resolve ${purposeOfEntry} "${cpEntry}". ${e.getMessage}""", e)
          ex.buildScript = Some(scriptFile)
          throw ex
      }

      files.find(file => !file.exists).map { file =>
        // need to resolve cpEntry via project
        val target = project.findOrCreateTarget(targetRef = entryRef, isImplicit = true)
        try {
          SBuildRunner.preorderedDependenciesTree(target)(project)
        } catch {
          case e: Exception =>
            val ex = new ProjectConfigurationException(s"""Could not resolve ${purposeOfEntry} "${cpEntry}". ${e.getMessage}""", e)
            ex.buildScript = Some(scriptFile)
            throw ex
        }
      }
      files
    }

  def readAdditionalClasspath(project: Project): Array[String] = {
    log.log(LogLevel.Debug, "About to find additional classpath entries.")
    val cp = readAnnotationWithVarargAttribute(annoName = "classpath", valueName = "value")
    log.log(LogLevel.Debug, "Using additional classpath entries: " + cp.mkString(", "))

    lazy val httpHandler = {
      var downloadDir: File = new File(targetBaseDir, "http")
      if (!downloadDir.isAbsolute) {
        downloadDir = downloadDir.getAbsoluteFile
      }
      new HttpSchemeHandlerBase(downloadDir)
    }

    resolveViaProject(cp, project, "@classpath entry").map { _.getPath }

    //    cp.map { entry =>
    //      if (entry.startsWith("http:")) {
    //        // we need to download it
    //        log.log(LogLevel.Debug, "Classpath entry is a HTTP resource: " + entry)
    //        val path = entry.substring("http:".length, entry.length)
    //        val file = httpHandler.localFile(path)
    //
    //        if (!file.exists) {
    //          downloadCache.map { cache =>
    //            try {
    //              val url = new URL(entry)
    //              if (cache.hasEntry(url)) {
    //                log.log(LogLevel.Debug, "Resolving classpath entry from download cache: " + url)
    //                val cachedEntry = cache.getEntry(url)
    //
    //                Path.normalize(file).getParentFile.mkdirs
    //                file.createNewFile
    //
    //                val fileOutputStream = new FileOutputStream(file)
    //                try {
    //                  fileOutputStream.getChannel.transferFrom(
    //                    new FileInputStream(cachedEntry).getChannel, 0, Long.MaxValue)
    //                } finally {
    //                  if (fileOutputStream != null) fileOutputStream.close
    //                }
    //              }
    //            } catch {
    //              case e: Exception =>
    //                log.log(LogLevel.Debug, "Could not use download cache for resource: " + entry + "\n" + e)
    //                if (file.exists) file.delete
    //            }
    //          }
    //        }
    //
    //        if (!file.exists) {
    //          log.log(LogLevel.Debug, "Need to download: " + entry)
    //          httpHandler.download(path)
    //        }
    //        if (!file.exists) {
    //          println("Could not resolve classpath entry: " + entry)
    //        } else {
    //          log.log(LogLevel.Debug, "Resolved: " + entry + " => " + file)
    //          try {
    //            val url = new URL(entry)
    //            downloadCache.map { cache =>
    //              if (!cache.hasEntry(url)) {
    //                cache.registerEntry(url, file)
    //              }
    //            }
    //          } catch {
    //            case e: Exception =>
    //              log.log(LogLevel.Debug, "Could not use download cache for resource: " + entry + "\n" + e)
    //          }
    //        }
    //
    //        file.getPath
    //        // end http:
    //      } else {
    //        val fileEntry = Path.normalize(new File(entry), projectDir)
    //        if (!fileEntry.exists) {
    //          println("Could not found classpath entry: " + entry)
    //        }
    //        fileEntry.getPath
    //      }
    //    }
  }

  def useExistingCompiled(project: Project, classpath: Array[String]): Any = {
    log.log(LogLevel.Debug, "Loading compiled version of build script: " + scriptFile)
    val cl = new URLClassLoader(Array(targetDir.toURI.toURL) ++ classpath.map(cp => new File(cp).toURI.toURL), getClass.getClassLoader)
    log.log(LogLevel.Debug, "CLassLoader loads build script from URLs: " + cl.asInstanceOf[{ def getURLs: Array[URL] }].getURLs.mkString(", "))
    val clazz: Class[_] = cl.loadClass(scriptBaseName)
    val ctr = clazz.getConstructor(classOf[Project])
    val scriptInstance = ctr.newInstance(project)
    // We assume, that everything is done in constructor, so we are done here
    scriptInstance
  }

  def clean() {
    Util.delete(targetBaseDir)
  }
  def cleanScala() {
    Util.delete(targetDir)
  }

  def newCompile(classpath: Array[String], includes: Array[File]) {
    cleanScala()
    targetDir.mkdirs
    log.log(LogLevel.Info, "Compiling build script: " + scriptFile + (if (includes.isEmpty) "" else " and " + includes.size + " included files") + "...")

    compile(classpath.mkString(File.pathSeparator), includes)

    log.log(LogLevel.Debug, "Writing info file: " + infoFile)
    val info = <sbuild>
                 <sourceSize>{ scriptFile.length }</sourceSize>
                 <sourceLastModified>{ scriptFile.lastModified }</sourceLastModified>
                 <targetClassLastModified>{ targetClassFile.lastModified }</targetClassLastModified>
                 <sbuildVersion>{ SBuildVersion.version }</sbuildVersion>
                 <sbuildOsgiVersion>{ SBuildVersion.osgiVersion }</sbuildOsgiVersion>
                 <includes>
                   {
                     includes.map { include =>
                       <include>
                         <path>{ include.getPath }</path>
                         <lastModified>{ include.lastModified }</lastModified>
                       </include>
                     }
                   }
                 </includes>
               </sbuild>
    val file = new FileWriter(infoFile)
    xml.XML.write(file, info, "UTF-8", true, null)
    file.close

  }

  def compile(classpath: String, includes: Array[File]) {
    val params = Array("-classpath", classpath, "-g:vars", "-d", targetDir.getPath, scriptFile.getPath) ++ (includes.map { file => file.getPath })
    log.log(LogLevel.Debug, "Using additional classpath for scala compiler: " + compileClasspath.mkString(", "))
    val compilerClassloader = new URLClassLoader(compileClasspath.map { f => new File(f).toURI.toURL }, getClass.getClassLoader)

    def compileWithFsc() {
      val compileClient = compilerClassloader.loadClass("scala.tools.nsc.StandardCompileClient").newInstance
      //      import scala.tools.nsc.StandardCompileClient
      //      val compileClient = new StandardCompileClient
      val compileMethod = compileClient.asInstanceOf[Object].getClass.getMethod("process", Array(classOf[Array[String]]): _*)
      log.log(LogLevel.Debug, "Executing CompileClient with args: " + params.mkString(" "))
      val retVal = compileMethod.invoke(compileClient, params).asInstanceOf[Boolean]
      if (!retVal) throw new SBuildException("Could not compile build file " + scriptFile.getAbsolutePath + " with CompileClient. See compiler output.")
    }

    def compileWithoutFsc() {
      val compiler = compilerClassloader.loadClass("scala.tools.nsc.Main")
      val compilerMethod = compiler.getMethod("process", Array(classOf[Array[String]]): _*)
      log.log(LogLevel.Debug, "Executing Scala Compile with args: " + params.mkString(" "))
      compilerMethod.invoke(null, params)
      val reporterMethod = compiler.getMethod("reporter")
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
