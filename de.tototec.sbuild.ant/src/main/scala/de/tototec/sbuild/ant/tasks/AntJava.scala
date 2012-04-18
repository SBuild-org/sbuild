package de.tototec.sbuild.ant.tasks

import org.apache.tools.ant.taskdefs.Java
import de.tototec.sbuild.ant.AntProject
import de.tototec.sbuild.Project
import org.apache.tools.ant.types.Path
import java.io.File

object AntJava {
  def apply(className: String = null,
            classpath: Path = null,
            spawn: java.lang.Boolean = null,
            jar: File = null,
            args: String = null,
            fork: java.lang.Boolean = null,
            jvmArgs: String = null,
            jvm: String = null,
            dir: File = null,
            output: File = null,
            input: File = null,
            inputString: String = null,
            error: File = null,
            logError: java.lang.Boolean = null,
            maxMemory: String = null,
            jvmVersion: String = null,
            append: java.lang.Boolean = null,
            timeout: java.lang.Long = null)(implicit _project: Project) =
    new AntJava(
      className = className,
      classpath = classpath,
      spawn = spawn,
      jar = jar,
      args = args,
      fork = fork,
      jvmArgs = jvmArgs,
      jvm = jvm,
      dir = dir,
      output = output,
      input = input,
      inputString = inputString,
      error = error,
      logError = logError,
      maxMemory = maxMemory,
      jvmVersion = jvmVersion,
      append = append,
      timeout = timeout
    ).execute
}

class AntJava()(implicit _project: Project) extends Java {
  setProject(AntProject())

  def this(className: String = null,
           classpath: Path = null,
           spawn: java.lang.Boolean = null,
           jar: File = null,
           args: String = null,
           fork: java.lang.Boolean = null,
           jvmArgs: String = null,
           jvm: String = null,
           dir: File = null,
           output: File = null,
           input: File = null,
           inputString: String = null,
           error: File = null,
           logError: java.lang.Boolean = null,
           maxMemory: String = null,
           jvmVersion: String = null,
           append: java.lang.Boolean = null,
           timeout: java.lang.Long = null)(implicit _project: Project) {
    this
    if (className != null) setClassname(className)
    if (classpath != null) setClasspath(classpath)
    if (spawn != null) setSpawn(spawn.booleanValue)
    if (jar != null) setJar(jar)
    if (args != null) setArgs(args)
    if (fork != null) setFork(fork.booleanValue)
    if (jvmArgs != null) setJvmArgs(jvmArgs)
    if (jvm != null) setJvm(jvm)
    if (dir != null) setDir(dir)
    if (output != null) setOutput(output)
    if (input != null) setInput(input)
    if (inputString != null) setInputString(inputString)
    if (error != null) setError(error)
    if (logError != null) setLogError(logError.booleanValue)
    if (maxMemory != null) setMaxmemory(maxMemory)
    if (jvmVersion != null) setJVMVersion(jvmVersion)
    if (append != null) setAppend(append.booleanValue)
    if (timeout != null) setTimeout(timeout.longValue)
  }

  def setClassName(className: String) = setClassname(className)
  def setJvmArgs(jvmArgs: String) = setJvmargs(jvmArgs)
  def setJvmVersion(jvmVersion: String) = setJVMVersion(jvmVersion)
  def setMaxMemory(maxMemory: String) = setMaxmemory(maxMemory)
}