package de.tototec.sbuild.ant.tasks

import org.apache.tools.ant.taskdefs.Execute
import de.tototec.sbuild.Project
import de.tototec.sbuild.ant.AntProject
import java.io.File
import org.apache.tools.ant.taskdefs.ExecTask
import org.apache.tools.ant.types.Environment

object AntExec {
  def apply(executable: String = null,
            args: Array[String] = null,
            dir: File = null,
            spawn: java.lang.Boolean = null,
            os: String = null,
            osFamily: String = null,
            timeout: java.lang.Long = null,
            output: File = null,
            input: File = null,
            inputString: String = null,
            error: File = null,
            logError: java.lang.Boolean = null,
            failOnError: java.lang.Boolean = null,
            resolveExecutable: java.lang.Boolean = null,
            searchPath: java.lang.Boolean = null,
            vmLauncher: java.lang.Boolean = null,
            envs: Map[String, String] = null)(implicit _project: Project) =
    new AntExec(
      executable = executable,
      args = args,
      dir = dir,
      spawn = spawn,
      os = os,
      osFamily = osFamily,
      timeout = timeout,
      output = output,
      input = input,
      inputString = inputString,
      error = error,
      logError = logError,
      failOnError = failOnError,
      resolveExecutable = resolveExecutable,
      searchPath = searchPath,
      vmLauncher = vmLauncher,
      envs = envs
    ).execute
}

class AntExec()(implicit _project: Project) extends ExecTask {
  setProject(AntProject())

  def this(executable: String = null,
           args: Array[String] = null,
           dir: File = null,
           spawn: java.lang.Boolean = null,
           os: String = null,
           osFamily: String = null,
           timeout: java.lang.Long = null,
           output: File = null,
           input: File = null,
           inputString: String = null,
           error: File = null,
           logError: java.lang.Boolean = null,
           failOnError: java.lang.Boolean = null,
           resolveExecutable: java.lang.Boolean = null,
           searchPath: java.lang.Boolean = null,
           vmLauncher: java.lang.Boolean = null,
           // since 0.1.9002
           envs: Map[String, String] = null)(implicit _project: Project) {
    this
    if (executable != null) setExecutable(executable)
    if (args != null) args foreach { arg =>
      createArg.setValue(arg)
    }
    if (dir != null) setDir(dir)
    if (spawn != null) setSpawn(spawn.booleanValue)
    if (os != null) setOs(os)
    if (osFamily != null) setOsFamily(osFamily)
    if (timeout != null) setTimeout(timeout.longValue)
    if (output != null) setOutput(output)
    if (input != null) setInput(input)
    if (inputString != null) setInputString(inputString)
    if (error != null) setError(error)
    if (logError != null) setLogError(logError.booleanValue)
    if (failOnError != null) setFailonerror(failOnError.booleanValue)
    if (resolveExecutable != null) setResolveExecutable(resolveExecutable.booleanValue)
    if (searchPath != null) setSearchPath(searchPath.booleanValue)
    if (vmLauncher != null) setVMLauncher(vmLauncher.booleanValue)
    if (envs != null) envs.foreach {
      case (key, value) =>
        val variable = new Environment.Variable()
        variable.setKey(key)
        variable.setValue(value)
        addEnv(variable)
    }
  }

  def setFailOnError(failOnError: Boolean) = setFailonerror(failOnError)
  def setVmLauncher(vmLauncher: Boolean) = setVMLauncher(vmLauncher)
}
