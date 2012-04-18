import de.tototec.sbuild._
import de.tototec.sbuild.ant._
import de.tototec.sbuild.ant.tasks._
import de.tototec.sbuild.TargetRefs._

@version("0.0.1")
@classpath(
  "http://repo1.maven.org/maven2/org/apache/ant/ant/1.8.3/ant-1.8.3.jar"
)
class SBuild(implicit project: Project) {

  SchemeHandler("mvn", new MvnSchemeHandler(Path("sbuild/mvn")))
  SchemeHandler("http", new HttpSchemeHandler(Path(".sbuild/http")))

  val version = "0.0.1-SNAPSHOT"
  val scalaVersion = "2.9.2"

  val binJar = "de.tototec.sbuild/target/de.tototec.sbuild-" + version + ".jar"
  val antJar = "de.tototec.sbuild.ant/target/de.tototec.sbuild.ant-" + version + ".jar"
  val cmdOptionJar = "http://cmdoption.tototec.de/cmdoption/attachments/download/3/de.tototec.cmdoption-0.1.0.jar"
  val scalaJar = "mvn:org.scala-lang:scala-library:" + scalaVersion
  val scalaCompilerJar = "mvn:org.scala-lang:scala-compiler:" + scalaVersion

  val distName = "sbuild-" + version
  val distDir = "target/" + distName

  Module("de.tototec.sbuild")
  Module("de.tototec.sbuild.ant")

  Target("phony:clean") exec {
    AntDelete(dir = Path("target"))
  } help "Clean all"

  Target("phony:all") dependsOn ("target/" + distName + "-dist.zip") help "Build all"

  Target("target/" + distName + "-dist.zip") dependsOn "createDistDir" exec { ctx: TargetContext =>
    AntZip(destFile = ctx.targetFile.get, baseDir = Path("target"), includes = distName + "/**")
  }

  Target("phony:createDistDir") dependsOn "copyJars" ~ (distDir + "/bin/sbuild")
  
  Target("phony:copyJars") dependsOn (binJar ~ antJar ~ cmdOptionJar ~ scalaJar ~ scalaCompilerJar) exec { ctx: TargetContext =>
    ctx.fileDependencies foreach { file => 
      AntCopy(file = file, toDir = Path(distDir + "/lib"))
    }
  }

  Target(distDir + "/bin/sbuild") exec { ctx: TargetContext =>
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

java -cp "${SBUILD_HOME}/lib/scala-library-""" + scalaVersion + """.jar:${SBUILD_HOME}/lib/de.tototec.cmdoption-0.1.0.jar:${SBUILD_HOME}/lib/de.tototec.sbuild-""" + version + """.jar" de.tototec.sbuild.runner.SBuildRunner \
--sbuild-cp "${SBUILD_HOME}/lib/de.tototec.sbuild-""" + version + """.jar" \
--compile-cp "${SBUILD_HOME}/lib/scala-compiler-""" + scalaVersion + """.jar" \
--project-cp "${SBUILD_HOME}/lib/de.tototec.sbuild.ant-""" + version + """.jar" \
"$@"

unset SBUILD_HOME
"""
    AntEcho(message = sbuildSh, file = ctx.targetFile.get)
    AntChmod(file = ctx.targetFile.get, perm = "+x")
  }

}
