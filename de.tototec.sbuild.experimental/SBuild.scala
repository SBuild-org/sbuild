import de.tototec.sbuild._
import de.tototec.sbuild.ant._
import de.tototec.sbuild.ant.tasks._
import de.tototec.sbuild.TargetRefs._

@version("0.4.0")
@include("../SBuildConfig.scala")
@classpath("mvn:org.apache.ant:ant:1.8.4")
class SBuild(implicit _project: Project) {

  import SBuildConfig.{ sbuildVersion, compilerPath, scalaVersion }

  val namespace = "de.tototec.sbuild.experimental"
  val jar = s"target/${namespace}-${sbuildVersion}.jar"
  val sourcesZip = s"target/${namespace}-${sbuildVersion}-sources.jar"

  val aetherVersion = "0.9.0.M2"
  val wagonVersion = "2.4"
  val aetherCp =
    s"mvn:org.eclipse.aether:aether-api:${aetherVersion}" ~
      s"mvn:org.eclipse.aether:aether-spi:${aetherVersion}" ~
      s"mvn:org.eclipse.aether:aether-util:${aetherVersion}" ~
      s"mvn:org.eclipse.aether:aether-impl:${aetherVersion}" ~
      s"mvn:org.eclipse.aether:aether-connector-file:${aetherVersion}" ~
      s"mvn:org.eclipse.aether:aether-connector-asynchttpclient:${aetherVersion}" ~
      s"mvn:org.eclipse.aether:aether-connector-wagon:${aetherVersion}" ~
      "mvn:io.tesla.maven:maven-aether-provider:3.1.2" ~
      s"mvn:org.apache.maven.wagon:wagon-provider-api:${wagonVersion}" ~
      s"mvn:org.apache.maven.wagon:wagon-http:${wagonVersion}" ~
      s"mvn:org.apache.maven.wagon:wagon-file:${wagonVersion}" ~
      s"mvn:org.apache.maven.wagon:wagon-ssh:${wagonVersion}" ~
      "mvn:org.sonatype.maven:wagon-ahc:1.2.1" ~
      s"mvn:org.apache.maven.wagon:wagon-http-shared4:${wagonVersion}" ~
      s"mvn:org.codehaus.plexus:plexus-component-annotations:1.5.5" ~
      s"mvn:org.apache.httpcomponents:httpclient:4.2.5" ~
      s"mvn:org.apache.httpcomponents:httpcore:4.2.4" ~
      "mvn:javax.inject:javax.inject:1" ~
      "mvn:com.ning:async-http-client:1.6.5" ~
      "mvn:io.tesla.maven:maven-model:3.1.0" ~
      "mvn:io.tesla.maven:maven-model-builder:3.1.0" ~
      "mvn:io.tesla.maven:maven-repository-metadata:3.1.0" ~
      "mvn:org.jboss.netty:netty:3.2.5.Final" ~
      "mvn:org.eclipse.sisu:org.eclipse.sisu.inject:0.0.0.M1" ~
      "mvn:org.eclipse.sisu:org.eclipse.sisu.plexus:0.0.0.M1" ~
      "mvn:org.codehaus.plexus:plexus-classworlds:2.4" ~
      "mvn:org.codehaus.plexus:plexus-interpolation:1.16" ~
      "mvn:org.codehaus.plexus:plexus-utils:2.1" ~
      "mvn:org.sonatype.sisu:sisu-guava:0.9.9" ~
      "mvn:org.sonatype.sisu:sisu-guice:3.1.0" ~
      "mvn:org.slf4j:slf4j-api:1.6.2"

  val compileCp =
    s"mvn:org.scala-lang:scala-library:${scalaVersion}" ~
      s"../de.tototec.sbuild/target/de.tototec.sbuild-${sbuildVersion}.jar" ~
      aetherCp

  ExportDependencies("eclipse.classpath", compileCp)

  val sources = "scan:src/main/scala" ~ "scan:target/generated-scala"

  Target("phony:all") dependsOn jar ~ sourcesZip ~ aetherImplJar ~ aetherJar

  Target("phony:clean").evictCache exec {
    AntDelete(dir = Path("target"))
  }

  val versionScala = Target("target/generated-scala/Version.scala") dependsOn _project.projectFile ~ Path("../SBuildConfig.scala") exec { ctx: TargetContext =>
    AntMkdir(dir = ctx.targetFile.get.getParentFile)
    AntEcho(file = ctx.targetFile.get, message = s"""// GENERATED
      package ${namespace} {
      package aether {
      private object InternalConstants {
        def version = "${sbuildVersion}"  
        def aetherImplJarName = s"${namespace}.aether.impl-${sbuildVersion}.jar"
      }
      }
      }"""
    )
  }

  Target("scan:target/generated-scala") dependsOn versionScala

  Target("phony:compile").cacheable dependsOn SBuildConfig.compilerPath ~ compileCp ~ sources exec {
    val output = "target/classes"
    addons.scala.Scalac(
      compilerClasspath = compilerPath.files,
      classpath = compileCp.files,
      sources = sources.files,
      destDir = Path(output),
      unchecked = true, deprecation = true, debugInfo = "vars"
    )
  }

  Target(jar) dependsOn "compile" ~ "scan:src/main/resources" ~ "LICENSE.txt" exec { ctx: TargetContext =>
    new AntJar(destFile = ctx.targetFile.get, baseDir = Path("target/classes")) {
      if (Path("src/main/resources").exists) add(AntFileSet(dir = Path("src/main/resources")))
      add(AntFileSet(file = Path("LICENSE.txt")))
    }.execute
  }

  Target(sourcesZip) dependsOn "scan:src/main/scala" ~ "scan:LICENSE.txt" exec { ctx: TargetContext =>
    AntZip(destFile = ctx.targetFile.get, fileSets = Seq(
      AntFileSet(dir = Path("src/main/scala")),
      AntFileSet(file = Path("LICENSE.txt"))
    ))
  }

  Target("phony:scaladoc").cacheable dependsOn compilerPath ~ compileCp ~ sources exec {
    addons.scala.Scaladoc(
      scaladocClasspath = compilerPath.files,
      classpath = compileCp.files,
      sources = sources.files,
      destDir = Path("target/scaladoc"),
      deprecation = true, unchecked = true, implicits = true,
      docVersion = sbuildVersion,
      docTitle = s"SBuild Experimental API Reference"
    )
  }

  lazy val aetherJar = Target(s"target/de.tototec.sbuild.experimental.aether-${sbuildVersion}.jar") dependsOn
    "compile" ~ "LICENSE.txt" ~ aetherImplJar exec { ctx: TargetContext =>
      AntJar(
        destFile = ctx.targetFile.get,
        baseDir = Path("target/classes"),
        includes = "de/tototec/sbuild/experimental/aether/*.class",
        fileSets = Seq(
          AntFileSet(file = Path("LICENSE.txt")),
          AntFileSet(file = aetherImplJar.files.head)
        )
      )
    }

  lazy val aetherImplJar = Target(s"target/de.tototec.sbuild.experimental.aether.impl-${sbuildVersion}.jar") dependsOn
    "compile" ~ "LICENSE.txt" exec { ctx: TargetContext =>
      AntJar(
        destFile = ctx.targetFile.get,
        baseDir = Path("target/classes"),
        includes = "de/tototec/sbuild/experimental/aether/impl/*.class",
        fileSet = AntFileSet(file = Path("LICENSE.txt"))
      )
    }

}
