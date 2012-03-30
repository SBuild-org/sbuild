package de.tototec.sbuild.runner

import scala.annotation.tailrec
import java.net.URLClassLoader
import de.tototec.sbuild.Util
import java.io.FileWriter
import java.io.LineNumberReader
import java.io.FileReader
import de.tototec.sbuild.Target
import scala.tools.nsc.settings.MutableSettings
import scala.tools.nsc.Settings
import scala.tools.nsc.interpreter.IMain
import java.io.File
import de.tototec.sbuild.Project
import de.tototec.sbuild.SBuildException
import java.net.URL
import de.tototec.sbuild.HttpSchemeHandler

class ProjectScript(scriptFile: File, compileClasspath: String) {

  val buildTargetDir = ".sbuild";
  val buildFileTargetDir = ".sbuild/scala";

  val scriptBaseName = scriptFile.getName.substring(0, scriptFile.getName.length - 6)
  lazy val targetBaseDir: File = new File(scriptFile.getParentFile, buildTargetDir)
  lazy val targetDir: File = new File(scriptFile.getParentFile, buildFileTargetDir)
  lazy val targetClassFile = new File(targetDir, scriptBaseName + ".class")
  lazy val infoFile = new File(targetDir, "sbuild.info.xml")

  def checkFile() = if (!scriptFile.exists) {
    throw new RuntimeException("Could not find build file: " + scriptFile.getName + "\nSearched in: "
      + scriptFile.getAbsoluteFile.getParent)
  }

  def compileAndExecute(project: Project, experimentalWithSameClassLoader: Boolean = false): Any = {
    checkFile

    val infoFile = new File(targetDir, "sbuild.info.xml")

    val addCp: Array[String] = readAdditionalClasspath

    if (!checkInfoFileUpToDate) {
      val cp = addCp match {
        case Array() => compileClasspath
        case x => compileClasspath + ":" + x.mkString(":")
      }
      println("Compiling build script...")
      newCompile(cp)
    }

    useExistingCompiled(project, addCp, experimentalWithSameClassLoader)

  }

  def checkInfoFileUpToDate(): Boolean = {
    infoFile.exists && {
      val info = xml.XML.loadFile(infoFile)

      val sourceSize = (info \ "sourceSize").text.toLong
      val sourceLastModified = (info \ "sourceLastModified").text.toLong
      val targetClassLastModified = (info \ "targetClassLastModified").text.toLong
      val sbuildVersion = (info \ "sbuildVersion").text

      scriptFile.length == sourceSize &&
        scriptFile.lastModified == sourceLastModified &&
        targetClassFile.lastModified == targetClassLastModified &&
        sbuildVersion == SBuild.version
    }
  }

  def readAnnotationWithSingleArrayAttribute(annoName: String, valueName: String): Array[String] = {
    import scala.tools.nsc.io.{ File => SFile }
    var inClasspath = false
    var skipRest = false
    var it = SFile(scriptFile).lines
    var annoLine = ""
    while (!skipRest && it.hasNext) {
      var line = it.next.trim
      if (inClasspath && line.endsWith(")")) {
        skipRest = true
        annoLine = annoLine + " " + line.substring(0, line.length - 1).trim
      }
      if (line.startsWith("@" + annoName + "(")) {
        line = line.substring(11).trim
        if (line.endsWith(")")) {
          line = line.substring(0, line.length - 1).trim
          skipRest = true
        }
        inClasspath = true
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
      if (annoLine.startsWith("Array(") && annoLine.endsWith(")")) {
        annoLine = annoLine.substring(6, annoLine.length - 1)
      } else {
        throw new RuntimeException("Expected a 'Array(...) expression, but got: " + annoLine)
      }

      val annoItems = annoLine.split(",")
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

  def readAnnotationWithVarargAttribute(annoName: String, valueName: String): Array[String] = {
    import scala.tools.nsc.io.{ File => SFile }
    var inClasspath = false
    var skipRest = false
    var it = SFile(scriptFile).lines
    var annoLine = ""
    while (!skipRest && it.hasNext) {
      var line = it.next.trim
      if (inClasspath && line.endsWith(")")) {
        skipRest = true
        annoLine = annoLine + " " + line.substring(0, line.length - 1).trim
      }
      if (line.startsWith("@" + annoName + "(")) {
        line = line.substring(11).trim
        if (line.endsWith(")")) {
          line = line.substring(0, line.length - 1).trim
          skipRest = true
        }
        inClasspath = true
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
      //      if (annoLine.startsWith("Array(") && annoLine.endsWith(")")) {
      //        annoLine = annoLine.substring(6, annoLine.length - 1)
      //      } else {
      //        throw new RuntimeException("Expected a 'Array(...) expression, but got: " + annoLine)
      //      }

      val annoItems = annoLine.split(",")
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

  def readAdditionalClasspath: Array[String] = {
    SBuild.verbose("About to find additional classpath entries.")
    val cp = readAnnotationWithVarargAttribute(annoName = "classpath", valueName = "value")
    SBuild.verbose("Using additional classpath entries: " + cp.mkString(", "))

    lazy val httpHandler = {
      var downloadDir: File = new File(targetBaseDir, "/http")
      if (!downloadDir.isAbsolute) {
        downloadDir = downloadDir.getAbsoluteFile
      }
      new HttpSchemeHandler(downloadDir.getPath)
    }

    cp.map { entry =>
      if (entry.startsWith("http:")) {
        // we need to download it
        SBuild.verbose("Classpath entry is a HTTP resource: " + entry)
        val path = entry.substring("http:".length, entry.length)
        val file = httpHandler.localFile(path)
        if (!file.exists) {
          SBuild.verbose("Need to download: " + entry)
          httpHandler.resolve(path) match {
            case Some(t: Throwable) => throw t
            case _ =>
          }
        }
        SBuild.verbose("Resolved: " + entry + " => " + file)
        file.getPath
      } else {
        entry
      }
    }
  }

  def readAdditionalInclude: Array[String] = {
    SBuild.verbose("About to find additional include files.")
    val cp = readAnnotationWithSingleArrayAttribute(annoName = "include", valueName = "value")
    SBuild.verbose("Using additional include files: " + cp.mkString(", "))
    cp
  }

  def useExistingCompiled(project: Project, classpath: Array[String], experimentalWithSameClassLoader: Boolean): Any = {
    SBuild.verbose("Loading compiled version of build script: " + scriptFile)
    val cl = experimentalWithSameClassLoader match {
      case false => new SBuildURLClassLoader(Array(targetDir.toURI.toURL) ++ classpath.map(cp => new File(cp).toURI.toURL), getClass.getClassLoader)
      case true =>
        project.getClass.getClassLoader match {
          case c: { def addURL(url: URL) } =>
            c.addURL(targetDir.toURI.toURL)
            classpath.map { new File(_).toURI.toURL }.foreach {
              c.addURL(_)
            }
            c
          case c => throw new RuntimeException("Unsupported ClassLoader: " + c)
        }
    }
    SBuild.verbose("CLassLoader loads build script from URLs: " + cl.asInstanceOf[{ def getURLs: Array[URL] }].getURLs.mkString(", "))
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

  def newCompile(classpath: String) {
    cleanScala()
    targetDir.mkdirs
    SBuild.verbose("Compiling build script: " + scriptFile)

    compile_with_fsc(classpath)

    SBuild.verbose("Writing info file: " + infoFile)
    val info = <sbuild>
                 <sourceSize>{ scriptFile.length }</sourceSize>
                 <sourceLastModified>{ scriptFile.lastModified }</sourceLastModified>
                 <targetClassLastModified>{ targetClassFile.lastModified }</targetClassLastModified>
                 <sbuildVersion>{ SBuild.version }</sbuildVersion>
               </sbuild>
    val file = new FileWriter(infoFile)
    xml.XML.write(file, info, "UTF-8", true, null)
    file.close

  }

  def compile_with_fsc(classpath: String) {
    val params = Array("-classpath", classpath, "-g:vars", "-d", new File(buildFileTargetDir).getAbsolutePath, scriptFile.getAbsolutePath)
    //    if(SBuild.verbose) {
    //      params = Array("-verbose") ++ params
    //    }
    val useCmdline = false

    if (!useCmdline) {
      import scala.tools.nsc.StandardCompileClient
      val compileClient = new StandardCompileClient
      if (!compileClient.process(params)) throw new SBuildException("Could not compile build file " + scriptFile.getName + " with CompileClient")
    } else {
      import sys.process.Process
      val retCode = Process(Array("fsc") ++ params) !

      if (retCode != 0) throw new SBuildException("Could not compile build file " + scriptFile.getName + " with fsc")
    }
  }

}