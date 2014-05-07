import de.tototec.sbuild._
import de.tototec.sbuild.ant._
import de.tototec.sbuild.ant.tasks._
import de.tototec.sbuild.TargetRefs._
import java.io.File

@version("0.4.0")
@include("../SBuildConfig.scala")
@classpath("mvn:org.apache.ant:ant:1.8.4")
class SBuild(implicit _project: Project) {

  import SBuildConfig._

  val binJar = s"../org.sbuild/target/org.sbuild-${sbuildVersion}.jar"
  val runnerJar = s"../org.sbuild.runner/target/org.sbuild.runner-${sbuildVersion}.jar"
  val antJar = s"../org.sbuild.ant/target/org.sbuild.ant-${sbuildVersion}.jar"
  val addonsJar = s"../org.sbuild.addons/target/org.sbuild.addons-${sbuildVersion}.jar"
  val pluginsJar = s"../org.sbuild.plugins/target/org.sbuild.plugins-${sbuildVersion}.jar"
  val scriptCompilerJar = s"../org.sbuild.scriptcompiler/target/org.sbuild.scriptcompiler-${sbuildVersion}.jar"
  val compilerPluginJar = s"../org.sbuild.compilerplugin/target/org.sbuild.compilerplugin-${sbuildVersion}.jar"
  val bootstrapJar = s"../org.sbuild.runner.bootstrap/target/org.sbuild.runner.bootstrap-${sbuildVersion}.jar"

  val distName = s"sbuild-${sbuildVersion}"
  val distDir = "target/" + distName

  val distZip = "target/" + distName + "-dist.zip"

  val classpathProperties = distDir + "/lib/classpath.properties"

  val javaOptions = "-XX:MaxPermSize=256m"

  val sbuildRunnerClass = "org.sbuild.runner.SBuildRunner"
  val sbuildRunnerLibs = scalaLibrary ~ scalaXml ~ cmdOption ~ jansi ~ binJar ~ runnerJar
  val sbuildRunnerDebugLibs = sbuildRunnerLibs ~ SBuildConfig.slf4jApi ~ SBuildConfig.logbackCore ~ SBuildConfig.logbackClassic ~ SBuildConfig.jclOverSlf4j ~ SBuildConfig.log4jOverSlf4j

  Target("phony:clean").evictCache exec {
    AntDelete(dir = Path("target"))
  }

  Target("phony:all") dependsOn distZip

  Target("phony:dist") dependsOn distZip

  Target(distZip) dependsOn "createDistDir" exec { ctx: TargetContext =>
    AntZip(destFile = ctx.targetFile.get, baseDir = Path("target"), includes = distName + "/**")
  }

  Target("phony:createDistDir") dependsOn "copyJars" ~ classpathProperties ~
      s"${distDir}/bin/sbuild" ~ s"${distDir}/bin/sbuild.bat" ~
      s"${distDir}/bin/sbuild-debug" ~ s"${distDir}/bin/sbuild-debug.bat" ~
      "../ChangeLog.txt" ~ "../LICENSE.txt" ~ "logback-debug.xml" exec {
    AntCopy(file = "../LICENSE.txt".files.head, toDir = Path(distDir + "/doc"))
    AntCopy(file = "../ChangeLog.txt".files.head, toDir = Path(distDir + "/doc"))
    AntCopy(file = "logback-debug.xml".files.head, toDir = Path(distDir + "/lib"))
  }

  Target("phony:copyJars").cacheable dependsOn cmdOption ~ SBuildConfig.compilerPath ~
      binJar ~ runnerJar ~ antJar ~ addonsJar ~ compilerPluginJar ~ scriptCompilerJar ~ jansi ~
      sbuildUnzipPlugin ~ sbuildHttpPlugin ~ sbuildSourceSchemePlugin ~ bootstrapJar ~
      sbuildRunnerDebugLibs exec { ctx: TargetContext =>
    ctx.fileDependencies.distinct.foreach { file =>
      val targetFile = Path(distDir, "lib", file.getName)
      AntCopy(file = file, toFile = targetFile)
      ctx.attachFile(targetFile)
    }
  }

  Target(classpathProperties) dependsOn
    _project.projectFile ~
    binJar ~ runnerJar ~ compilerPluginJar ~ scriptCompilerJar ~
    bootstrapJar ~ sbuildUnzipPlugin ~ sbuildHttpPlugin ~ sbuildSourceSchemePlugin ~
    antJar ~ addonsJar ~
    cmdOption ~ jansi ~
    scalaLibrary ~ scalaCompiler ~ scalaReflect ~ scalaXml exec { ctx: TargetContext =>
    val properties = s"""|# Classpath configuration for SBuild ${sbuildVersion}
      |# sbuildClasspath - Used to load the SBuild API
      |sbuildClasspath = ${binJar.files.head.getName}
      |# compileClasspath - Used to load the compiler to compile the buildfile in initialization phase
      |compileClasspath = ${scalaCompiler.files.head.getName}:${scalaReflect.files.head.getName}:${scriptCompilerJar.files.head.getName}
      |# projectCompileClasspath - Used to compile the buildfiles
      |projectCompileClasspath = ${scalaLibrary.files.head.getName}
      |# projectRuntimeClasspath - Used to load the buildfiles
      |projectRuntimeClasspath =
      |# embeddedClasspath - Used to load the SBuild embedded API and all its dependencies, e.g. from IDE's
      |embeddedClasspath = ${binJar.files.head.getName}:${runnerJar.files.head.getName}:${scalaXml.files.head.getName}:${cmdOption.files.head.getName}:${jansi.files.head.getName}
      |# compilerPluginJar - Used by the build file compiler to load the compiler plugin which extracts additional infos
      |compilerPluginJar = ${compilerPluginJar.files.head.getName}
      |projectBootstrapJars = ${bootstrapJar.files.head.getName}
      |projectBootstrapClasspath = ${(runnerJar ~ sbuildUnzipPlugin ~ sbuildHttpPlugin ~ sbuildSourceSchemePlugin).files.map(_.getName).mkString(":")}
      |"""
    AntMkdir(dir = ctx.targetFile.get.getParentFile)
    AntEcho(file = ctx.targetFile.get, message = properties.stripMargin)
  }

  def formatClasspathUnix(sbuildRunnerLibs: Seq[File]) = sbuildRunnerLibs.map("${SBUILD_HOME}/lib/" + _.getName).mkString(":")
  def formatClasspathWin(sbuildRunnerLibs: Seq[File]) = sbuildRunnerLibs.map("%SBUILD_HOME%\\lib\\" + _.getName).mkString(";")

  def sbuildSh(sbuildRunnerLibs: Seq[File], javaOptions: String) =
    ("""|#!/bin/sh
      |
      |# Determime SBUILD_HOME (adapted from maven)
      |if [ -z "$SBUILD_HOME" ] ; then
      |  ## resolve links - $0 may be a link to maven's home
      |  PRG="$0"
      |
      |  # need this for relative symlinks
      |  while [ -h "$PRG" ] ; do
      |    ls=`ls -ld "$PRG"`
      |    link=`expr "$ls" : '.*-> \(.*\)$'`
      |    if expr "$link" : '/.*' > /dev/null; then
      |      PRG="$link"
      |    else
      |      PRG="`dirname "$PRG"`/$link"
      |    fi
      |  done
      |
      |  saveddir=`pwd`
      |
      |  SBUILD_HOME=`dirname "$PRG"`/..
      |
      |  # make it fully qualified
      |  SBUILD_HOME=`cd "$SBUILD_HOME" && pwd`
      |
      |  cd "$saveddir"
      |  # echo Using sbuild at $SBUILD_HOME
      |fi
      |
      |#Determine Java runtime
      |if [ -n "$JAVA_HOME" ] ; then
      |  JRE=${JAVA_HOME}/bin/java
      |else
      |  JRE="java"
      |fi
      |
      |""" +
     s"""exec $${JRE} ${javaOptions} $${SBUILD_JAVA_OPTIONS} -cp "${formatClasspathUnix(sbuildRunnerLibs)}" ${sbuildRunnerClass} """ +
     """--sbuild-home "${SBUILD_HOME}" ${SBUILD_OPTS} "$@"
      |
      |unset SBUILD_HOME
      |""").stripMargin

  def sbuildBat(sbuildRunnerLibs: Seq[File], javaOptions: String) =
    ("""|@echo off
         |
         |set ERROR_CODE=0
         |
         |@REM set local scope for the variables with windows NT shell
         |if "%OS%"=="Windows_NT" @setlocal
         |if "%OS%"=="WINNT" @setlocal
         |
         |@REM Find SBuild home dir
         |if NOT "%SBUILD_HOME"=="" goto valSHome
         |
         |if "%OS%"=="Windows_NT" SET "SBUILD_HOME=%~dp0.."
         |if "%OS%"=="WINNT" SET "SBUILD_HOME=%~dp0.."
         |if not "%SBUILD_HOME%"=="" goto valSHome
         |
         |echo.
         |echo ERROR: SBUILD_HOME not found in your environment.
         |echo Please set the SBUILD_HOME variable in your environment to match the
         |echo location of the SBuild installation
         |echo.
         |goto error
         |
         |:valSHome
         |
         |:stripSHome
         |if not "_%SBUILD_HOME:~-1%"=="_\" goto checkSBat
         |set "SBUILD_HOME=%SBUILD_HOME:~0,-1%"
         |goto stripSHome
         |
         |:checkSBat
         |if exist "%SBUILD_HOME%\bin\sbuild.bat" goto init
         |
         |echo.
         |echo ERROR: SBUILD_HOME is set to an invalid directory.
         |echo SBUILD_HOME = %SBUILD_HOME%
         |echo Please set the SBUILD_HOME variable in your environment to match the
         |echo location of the SBuild installation
         |echo.
         |goto error
         |
         |:init
         |@REM Decide how to startup depending on the version of windows
         |@REM -- Windows NT with Novell Login
         |if "%OS%"=="WINNT" goto WinNTNovell
         |@REM -- Win98ME
         |if NOT "%OS%"=="Windows_NT" goto Win9xArg
         |
         |:WinNTNovell
         |@REM -- 4 NT shell
         |if "%@eval[2+2]"=="4" goto 4 NTArgs
         |@REM -- Regular WinNT shell
         |set SBUILD_CMD_LINE_ARGS=%*
         |goto endInit
         |@REM The 4 NT Shell from jp software
         |
         |:4 NTArgs
         |set SBUILD_CMD_LINE_ARGS=%$
         |goto endInit
         |
         |:Win9xArg
         |@REM Slurp the command line arguments . This loop allows for an unlimited number
         |@REM of agruments (up to the command line limit, anyway).
         |set SBUILD_CMD_LINE_ARGS=
         |
         |:Win9xApp
         |if %1a == a goto endInit
         |set SBUILD_CMD_LINE_ARGS=%SBUILD_CMD_LINE_ARGS% %1%
         |shift
         |goto Win9xApp
         |
         |@REM Reaching here means variables are defined and arguments have been captured
         |:endInit
         |SET SBUILD_JAVA_EXE=java.exe
         |if NOT "%JAVA_HOME%"=="" SET SBUILD_JAVA_EXE=%JAVA_HOME%\bin\java.exe
         |
         |""" +
      s""""%SBUILD_JAVA_EXE%" ${javaOptions} %SBUILD_JAVA_OPTIONS% -cp "${formatClasspathWin(sbuildRunnerLibs)}" ${sbuildRunnerClass} --sbuild-home "%SBUILD_HOME%" %SBUILD_CMD_LINE_ARGS%
         |""" + """
         |goto end
         |
         |:error
         |set ERROR_CODE=1
         |
         |:end
         |if "%OS%"=="Windows_NT" @endlocal
         |if "%OS%"=="Windows_NT" goto :exit
         |if "%OS%"=="WINNT" @endlocal
         |if "%OS%"=="WINNT" goto :exit
         |
         |set SBUILD_JAVA_EXE=
         |set SBUILD_CMD_LINE_ARGS=
         |
         |:exit
         |cmd /C exit /B %ERROR_CODE%
         |""").stripMargin

  Target(distDir + "/bin/sbuild") dependsOn _project.projectFile ~ sbuildRunnerLibs exec { ctx: TargetContext =>
    AntEcho(file = ctx.targetFile.get, message = sbuildSh(sbuildRunnerLibs.files, javaOptions))
    AntChmod(file = ctx.targetFile.get, perm = "+x")
  }

  Target(distDir + "/bin/sbuild-debug") dependsOn _project.projectFile ~ sbuildRunnerDebugLibs exec { ctx: TargetContext =>
    AntEcho(
      file = ctx.targetFile.get,
      message = sbuildSh(sbuildRunnerDebugLibs.files, javaOptions + " -Dlogback.configurationFile=${SBUILD_HOME}/lib/logback-debug.xml")
    )
    AntChmod(file = ctx.targetFile.get, perm = "+x")
  }

  Target(distDir + "/bin/sbuild.bat") dependsOn _project.projectFile ~ sbuildRunnerLibs exec { ctx: TargetContext =>
    AntEcho(file = ctx.targetFile.get, message = sbuildBat(sbuildRunnerLibs.files, javaOptions))
  }

  Target(distDir + "/bin/sbuild-debug.bat") dependsOn _project.projectFile ~ sbuildRunnerDebugLibs exec { ctx: TargetContext =>
    AntEcho(
      file = ctx.targetFile.get,
      message = sbuildBat(sbuildRunnerDebugLibs.files, javaOptions + " -Dlogback.configurationFile=%SBUILD_HOME%\\lib\\logback-debug.xml"))
  }

}
