package de.tototec.sbuild.runner

import java.io.File
import java.io.FileWriter
import java.net.URL
import de.tototec.sbuild.Util
import de.tototec.sbuild.Project
import de.tototec.sbuild.SBuildException
import de.tototec.sbuild.HttpSchemeHandler

class ProjectScript(scriptFile: File, compileClasspath: String, additionalProjectClasspath: Array[String]) {

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

  def compileAndExecute(project: Project): Any = {
    checkFile

    val infoFile = new File(targetDir, "sbuild.info.xml")

    val addCp: Array[String] = additionalProjectClasspath ++ readAdditionalClasspath

    if (!checkInfoFileUpToDate) {
      val cp = addCp match {
        case Array() => compileClasspath
        case x => compileClasspath + ":" + x.mkString(":")
      }
      println("Compiling build script...")
      newCompile(cp)
    }

    useExistingCompiled(project, addCp)
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
        sbuildVersion == SBuildRunner.version
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
      if (inClasspath) {
        if (line.endsWith(")")) {
          skipRest = true
          line = line.substring(0, line.length - 1).trim
        }
        annoLine = annoLine + " " + line
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
    SBuildRunner.verbose("About to find additional classpath entries.")
    val cp = readAnnotationWithVarargAttribute(annoName = "classpath", valueName = "value")
    SBuildRunner.verbose("Using additional classpath entries: " + cp.mkString(", "))

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
        SBuildRunner.verbose("Classpath entry is a HTTP resource: " + entry)
        val path = entry.substring("http:".length, entry.length)
        val file = httpHandler.localFile(path)
        if (!file.exists) {
          SBuildRunner.verbose("Need to download: " + entry)
          httpHandler.resolve(path) match {
            case Some(t: Throwable) => throw t
            case _ =>
          }
        }
        if (!file.exists) {
          println("Could not resolve classpath entry: " + entry)
        } else {
          SBuildRunner.verbose("Resolved: " + entry + " => " + file)
        }
        file.getPath
      } else {
        if (!new File(entry).exists) {
          println("Could not found classpath entry: " + entry)
        }
        entry
      }
    }
  }

  def readAdditionalInclude: Array[String] = {
    SBuildRunner.verbose("About to find additional include files.")
    val cp = readAnnotationWithSingleArrayAttribute(annoName = "include", valueName = "value")
    SBuildRunner.verbose("Using additional include files: " + cp.mkString(", "))
    cp
  }

  def useExistingCompiled(project: Project, classpath: Array[String]): Any = {
    SBuildRunner.verbose("Loading compiled version of build script: " + scriptFile)
    val cl = new SBuildURLClassLoader(Array(targetDir.toURI.toURL) ++ classpath.map(cp => new File(cp).toURI.toURL), getClass.getClassLoader)
    SBuildRunner.verbose("CLassLoader loads build script from URLs: " + cl.asInstanceOf[{ def getURLs: Array[URL] }].getURLs.mkString(", "))
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
    SBuildRunner.verbose("Compiling build script: " + scriptFile)

    compile_with_fsc(classpath)

    SBuildRunner.verbose("Writing info file: " + infoFile)
    val info = <sbuild>
                 <sourceSize>{ scriptFile.length }</sourceSize>
                 <sourceLastModified>{ scriptFile.lastModified }</sourceLastModified>
                 <targetClassLastModified>{ targetClassFile.lastModified }</targetClassLastModified>
                 <sbuildVersion>{ SBuildRunner.version }</sbuildVersion>
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
      SBuildRunner.verbose("Executing CompileClient with args: " + params.mkString(" "))
      if (!compileClient.process(params)) throw new SBuildException("Could not compile build file " + scriptFile.getName + " with CompileClient")
    } else {
      import sys.process.Process
      val retCode = Process(Array("fsc") ++ params) !

      if (retCode != 0) throw new SBuildException("Could not compile build file " + scriptFile.getName + " with fsc")
    }
  }

}