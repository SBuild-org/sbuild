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

class ProjectScript(scriptFile: File, compileClasspath: String) {

  val buildFileTargetDir = ".sbuild";

  val scriptBaseName = scriptFile.getName.substring(0, scriptFile.getName.length - 6)
  lazy val targetDir: File = new File(scriptFile.getParentFile, buildFileTargetDir)
  lazy val targetClassFile = new File(targetDir, scriptBaseName + ".class")
  lazy val infoFile = new File(targetDir, "sbuild.info.xml")

  def checkFile() = if (!scriptFile.exists) {
    throw new RuntimeException("Could not find build file: " + scriptFile.getName + "\nSearched in: "
      + scriptFile.getAbsoluteFile.getParent)
  }

  def compileAndExecute(project: Project) {
    checkFile

    val infoFile = new File(targetDir, "sbuild.info.xml")

    val addCp: Array[String] = readAdditionalClasspath

    if (!checkInfoFileUpToDate) {
      val cp = addCp match {
        case Array() => compileClasspath
        case x => compileClasspath + ":" + x.mkString(":", ":", "")
      }
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
        sbuildVersion == SBuild.version
    }
  }

  def readAdditionalClasspath: Array[String] = {
    SBuild.verbose("About to find additional classpath entries.")
    import scala.tools.nsc.io.{ File => SFile }
    var inClasspath = false
    var skipRest = false
    var it = SFile(scriptFile).lines
    var cpLine = ""
    while (!skipRest && it.hasNext) {
      var line = it.next.trim
      if (inClasspath && line.endsWith(")")) {
        skipRest = true
        cpLine = cpLine + " " + line.substring(0, line.length - 1).trim
      }
      if (line.startsWith("@classpath(")) {
        line = line.substring(11).trim
        if (line.endsWith(")")) {
          line = line.substring(0, line.length - 1).trim
          skipRest = true
        }
        inClasspath = true
        cpLine = line
      }
    }

    cpLine = cpLine.trim

    if (cpLine.length > 0) {
      if (cpLine.startsWith("cp")) {
        cpLine = cpLine.substring(2).trim
        if (cpLine.startsWith("=")) {
          cpLine = cpLine.substring(1).trim
        } else {
          throw new RuntimeException("Expected a '=' sign but got a '" + cpLine(0) + "'")
        }
      }
      if (cpLine.startsWith("Array(") && cpLine.endsWith(")")) {
        cpLine = cpLine.substring(6, cpLine.length - 1)
      } else {
        throw new RuntimeException("Expected a 'Array(...) expression, but got: " + cpLine)
      }

      val cpItems = cpLine.split(",")
      val finalCpItems = cpItems map { item => item.trim } map { item =>
        if (item.startsWith("\"") && item.endsWith("\"")) {
          item.substring(1, item.length - 1)
        } else {
          throw new RuntimeException("Unexpection token found: " + item)
        }
      }
      SBuild.verbose("Using additional classpath entries: " + finalCpItems.mkString(", "))
      finalCpItems
    } else {
      Array()
    }
  }

  def useExistingCompiled(project: Project, classpath: Array[String]) {
    SBuild.verbose("Loading compiled version of build script: " + scriptFile)
    val cl = new URLClassLoader(Array(targetDir.toURI.toURL) ++ classpath.map(cp => new File(cp).toURI.toURL), getClass.getClassLoader)
    val clazz: Class[_] = cl.loadClass(scriptBaseName)
    val ctr = clazz.getConstructor(classOf[Project])
    val scriptInstance = ctr.newInstance(project)
    // We assume, that everything is done in constructor, so we are done here
  }

  def newCompile(classpath: String) {
    Util.delete(targetDir)
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
    val params = Array("-classpath", classpath, "-d", new File(".sbuild").getAbsolutePath, scriptFile.getAbsolutePath)
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

  def interpret() {
    checkFile

    SBuild.verbose("Interpreting build script: " + scriptFile)

    val settings = new Settings
    settings.classpath.append("target/classes")

    val main = new IMain(settings)
    main.addImports("de.tobiasroeser.jackage.sbuild.Goal._")
    main.bind("Goal", Target.getClass.getName, Target);

    io.Source.fromFile(scriptFile).getLines.map(line => main.interpret(line))

  }

}