import de.tototec.sbuild._
import de.tototec.sbuild.ant._
import de.tototec.sbuild.ant.tasks._
import de.tototec.sbuild.TargetRefs._

@version("0.1.0")
@classpath(
  "http://repo1.maven.org/maven2/org/apache/ant/ant/1.8.3/ant-1.8.3.jar"
)
class SBuild(implicit project: Project) {

  SchemeHandler("mvn", new MvnSchemeHandler(Path(Prop("mvn.repo", ".sbuild/mvn"))))
  SchemeHandler("http", new HttpSchemeHandler(Path(".sbuild/http")))

  val version = Prop("SBUILD_VERSION", "0.1.5")
  SetProp("SBUILD_VERSION", version)
  val osgiVersion = Prop("SBUILD_OSGI_VERSION", "0.1.5")
  SetProp("SBUILD_OSGI_VERSION", osgiVersion)

  val scalaVersion = "2.9.2"

  val binJar = "de.tototec.sbuild/target/de.tototec.sbuild.jar"
  val antJar = "de.tototec.sbuild.ant/target/de.tototec.sbuild.ant.jar"
  val addonsJar = "de.tototec.sbuild.addons/target/de.tototec.sbuild.addons.jar"
  val cmdOptionJar = "http://cmdoption.tototec.de/cmdoption/attachments/download/3/de.tototec.cmdoption-0.1.0.jar"
  val scalaJar = "mvn:org.scala-lang:scala-library:" + scalaVersion
  val scalaCompilerJar = "mvn:org.scala-lang:scala-compiler:" + scalaVersion

  val distName = "sbuild-" + version
  val distDir = "target/" + distName

  val distZip = "target/" + distName + "-dist.zip"

  val classpathProperties = distDir + "/lib/classpath.properties"

  val modules = Seq("de.tototec.sbuild", "de.tototec.sbuild.ant", "de.tototec.sbuild.eclipse.plugin", "de.tototec.sbuild.addons")
  modules.foreach { Module(_) }

  Target("phony:clean") dependsOn (modules.map(m => TargetRefs(m + "::clean")).reduceLeft(_ ~ _)) exec {
    AntDelete(dir = Path("target"))
  } help "Clean all"

  val eclipsePlugin = "de.tototec.sbuild.eclipse.plugin::target/de.tototec.sbuild.eclipse.plugin-" + osgiVersion + ".jar"

  Target("phony:all") dependsOn ((modules.map(m => TargetRefs(m + "::all")).reduceLeft(_ ~ _)) ~ distZip ~ eclipsePlugin) help "Build all"

  Target("phony:test") dependsOn ("de.tototec.sbuild::test") help "Run all tests"

  Target(distZip) dependsOn "createDistDir" exec { ctx: TargetContext =>
    AntZip(destFile = ctx.targetFile.get, baseDir = Path("target"), includes = distName + "/**")
  }

  Target("phony:createDistDir") dependsOn "copyJars" ~ classpathProperties ~ (distDir + "/bin/sbuild") ~ (distDir + "/bin/sbuild.bat") ~ "LICENSE.txt" exec {
    AntCopy(file = Path("LICENSE.txt"), toDir = Path(distDir + "/doc"))
    AntCopy(file = Path("ChangeLog.txt"), toDir = Path(distDir + "/doc"))
  }
  
  Target("phony:copyDeps") dependsOn (cmdOptionJar ~ scalaJar ~ scalaCompilerJar) exec { ctx: TargetContext =>
    ctx.fileDependencies foreach { file => 
      AntCopy(file = file, toDir = Path(distDir + "/lib"))
    }
  }

  Target("phony:copyJars") dependsOn ("copyDeps" ~ binJar ~ antJar ~ addonsJar) exec { ctx: TargetContext =>
    AntCopy(file = Path(binJar), toFile = Path(distDir + "/lib/de.tototec.sbuild-" + version + ".jar"))
    AntCopy(file = Path(antJar), toFile = Path(distDir + "/lib/de.tototec.sbuild.ant-" + version + ".jar"))
    AntCopy(file = Path(addonsJar), toFile = Path(distDir + "/lib/de.tototec.sbuild.addons-" + version + ".jar"))
  }

  Target(classpathProperties) exec { ctx: TargetContext =>
    val properties = """# Classpath configuration for SBuild """ + version + """
sbuildClasspath = de.tototec.sbuild-""" + version + """.jar
compileClasspath = scala-compiler-""" + scalaVersion + """.jar
projectClasspath = scala-library-""" + scalaVersion + """.jar:de.tototec.sbuild.ant-""" + version + """.jar:de.tototec.sbuild.addons-""" + version + """.jar
"""
    AntMkdir(dir = ctx.targetFile.get.getParentFile)
    AntEcho(message = properties, file = ctx.targetFile.get)
  }

  Target(distDir + "/bin/sbuild") dependsOn project.projectFile exec { ctx: TargetContext =>
    val sbuildSh = """#!/bin/sh

# Determime SBUILD_HOME (adapted from maven)
if [ -z "$SBUILD_HOME" ] ; then
  ## resolve links - $0 may be a link to maven's home
  PRG="$0"

  # need this for relative symlinks
  while [ -h "$PRG" ] ; do
    ls=`ls -ld "$PRG"`
    link=`expr "$ls" : '.*-> \(.*\)$'`
    if expr "$link" : '/.*' > /dev/null; then
      PRG="$link"
    else
      PRG="`dirname "$PRG"`/$link"
    fi
  done

  saveddir=`pwd`

  SBUILD_HOME=`dirname "$PRG"`/..

  # make it fully qualified
  SBUILD_HOME=`cd "$SBUILD_HOME" && pwd`

  cd "$saveddir"
  # echo Using sbuild at $SBUILD_HOME
fi

java -XX:MaxPermSize=128m -cp "${SBUILD_HOME}/lib/scala-library-""" + scalaVersion + """.jar:${SBUILD_HOME}/lib/de.tototec.cmdoption-0.1.0.jar:${SBUILD_HOME}/lib/de.tototec.sbuild-""" + version + """.jar" de.tototec.sbuild.runner.SBuildRunner \
--sbuild-home "${SBUILD_HOME}" "$@" 

unset SBUILD_HOME
"""
    AntEcho(message = sbuildSh, file = ctx.targetFile.get)
    AntChmod(file = ctx.targetFile.get, perm = "+x")
  }

  Target(distDir + "/bin/sbuild.bat") dependsOn project.projectFile exec { ctx: TargetContext =>
    val sbuildSh = """
@echo off

set ERROR_CODE=0

@REM set local scope for the variables with windows NT shell
if "%OS%"=="Windows_NT" @setlocal
if "%OS%"=="WINNT" @setlocal

@REM Find SBuild home dir
if NOT "%SBUILD_HOME"=="" goto valSHome

if "%OS%"=="Windows_NT" SET "SBUILD_HOME=%~dp0.."
if "%OS%"=="WINNT" SET "SBUILD_HOME=%~dp0.."
if not "%SBUILD_HOME%"=="" goto valSHome

echo.
echo ERROR: SBUILD_HOME not found in your environment.
echo Please set the SBUILD_HOME variable in your environment to match the
echo location of the SBuild installation
echo.
goto error
      
:valSHome

:stripSHome
if not "_%SBUILD_HOME:~-1%"=="_\" goto checkSBat
set "SBUILD_HOME=%SBUILD_HOME:~0,-1%"
goto stripSHome

:checkSBat
if exist "%SBUILD_HOME%\bin\sbuild.bat" goto init

echo.
echo ERROR: SBUILD_HOME is set to an invalid directory.
echo SBUILD_HOME = %SBUILD_HOME%
echo Please set the SBUILD_HOME variable in your environment to match the
echo location of the SBuild installation
echo.
goto error
      
:init
@REM Decide how to startup depending on the version of windows
@REM -- Windows NT with Novell Login
if "%OS%"=="WINNT" goto WinNTNovell
@REM -- Win98ME
if NOT "%OS%"=="Windows_NT" goto Win9xArg

:WinNTNovell
@REM -- 4 NT shell
if "%@eval[2+2]"=="4" goto 4 NTArgs
@REM -- Regular WinNT shell
set SBUILD_CMD_LINE_ARGS=%*
goto endInit
@REM The 4 NT Shell from jp software

:4 NTArgs
set SBUILD_CMD_LINE_ARGS=%$
goto endInit

:Win9xArg
@REM Slurp the command line arguments . This loop allows for an unlimited number
@REM of agruments (up to the command line limit, anyway).
set SBUILD_CMD_LINE_ARGS=

:Win9xApp
if %1a == a goto endInit
set SBUILD_CMD_LINE_ARGS=%SBUILD_CMD_LINE_ARGS% %1%
shift
goto Win9xApp

@REM Reaching here means variables are defined and arguments have been captured
:endInit
SET SBUILD_JAVA_EXE=java.exe
if NOT "%JAVA_HOME%"=="" SET SBUILD_JAVA_EXE=%JAVA_HOME%\bin\java.exe

%SBUILD_JAVA_EXE% -cp "%SBUILD_HOME%\lib\scala-library-""" + scalaVersion + """.jar;%SBUILD_HOME%\lib\de.tototec.cmdoption-0.1.0.jar;%SBUILD_HOME%\lib\de.tototec.sbuild-""" + version + """.jar" """ +
"""de.tototec.sbuild.runner.SBuildRunner """ +
"""--sbuild-home "%SBUILD_HOME%" """ +
// """--sbuild-cp "%SBUILD_HOME%\lib\de.tototec.sbuild-""" + version + """.jar" """ +
//"""--compile-cp "%SBUILD_HOME%\lib\scala-compiler-""" + scalaVersion + """.jar" """ +
// """--project-cp "%SBUILD_HOME%\lib\scala-library-""" + scalaVersion + """.jar;%SBUILD_HOME%\lib\de.tototec.sbuild.ant-""" + version + """.jar;%SBUILD_HOME%\lib\de.tototec.sbuild-addons-""" + version + """.jar" """ +
"""%SBUILD_CMD_LINE_ARGS%
      
goto end

:error
set ERROR_CODE=1
      
:end
if "%OS%"=="Windows_NT" @endlocal
if "%OS%"=="Windows_NT" goto :exit
if "%OS%"=="WINNT" @endlocal
if "%OS%"=="WINNT" goto :exit

set SBUILD_JAVA_EXE=
set SBUILD_CMD_LINE_ARGS=

:exit
cmd /C exit /B %ERROR_CODE%
"""
    AntEcho(message = sbuildSh, file = ctx.targetFile.get)
    AntChmod(file = ctx.targetFile.get, perm = "+x")
  }

}
