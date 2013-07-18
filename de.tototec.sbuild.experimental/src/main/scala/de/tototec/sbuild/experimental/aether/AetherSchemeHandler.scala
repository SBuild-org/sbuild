package de.tototec.sbuild.experimental.aether

import java.io.File
import java.net.URLClassLoader

import de.tototec.sbuild.LogLevel
import de.tototec.sbuild.MavenSupport.MavenGav
import de.tototec.sbuild.Project
import de.tototec.sbuild.SchemeResolver
import de.tototec.sbuild.TargetContext
import de.tototec.sbuild.TargetRefs
import de.tototec.sbuild.TargetRefs.fromString

object AetherSchemeHandler {

  case class Repository(name: String, layout: String, url: String)
  val CentralRepo = Repository("central", "default", "http://repo1.maven.org/maven2")

  def fullAetherCp(implicit project: Project): TargetRefs = {
    val aetherVersion = "0.9.0.M2"
    val wagonVersion = "2.4"
    val slf4jVersion = "1.7.5"

    import TargetRefs._

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
      "mvn:org.slf4j:slf4j-api:1.7.5" ~
      "mvn:org.slf4j:slf4j-simple:1.7.5"
  }

}

class AetherSchemeHandler(
  aetherClasspath: Seq[File] = Seq(),
  localRepoDir: File = new File(System.getProperty("user.home") + "/.m2/repository"),
  remoteRepos: Seq[AetherSchemeHandler.Repository] = Seq(AetherSchemeHandler.CentralRepo))(implicit project: Project)
    extends SchemeResolver {

  private[this] val worker: AetherSchemeHandlerWorker = {

    val thisClass = classOf[AetherSchemeHandler]

    val aetherClassLoader = aetherClasspath match {
      case null | Seq() => thisClass.getClassLoader
      case cp =>
        val cl = new URLClassLoader(cp.map { f => f.toURI().toURL() }.toArray, thisClass.getClassLoader)
        project.log.log(LogLevel.Debug, "Using aether classpath: " + cl.getURLs().mkString(", "))
        cl
    }

    try {
      val workerImplClass = aetherClassLoader.loadClass(thisClass.getPackage().getName() + "." + "impl.AetherSchemeHandlerWorkerImpl")
      val workerImplClassCtr = workerImplClass.getConstructor(classOf[File], classOf[Seq[AetherSchemeHandler.Repository]])
      val worker = workerImplClassCtr.newInstance(localRepoDir, remoteRepos).asInstanceOf[AetherSchemeHandlerWorker]
      worker
    } catch {
      case e: ClassNotFoundException =>
        // TODO: Lift exception into domain
        throw e
    }
  }

  def localPath(path: String): String = s"phony:aether-dependencies-${Integer.toHexString(hashCode())}-${path}"

  def resolve(path: String, targetContext: TargetContext) {
    try {

      val requestedDeps = path.split(",").map { p => MavenGav(p) }

      val files = worker.resolve(requestedDeps)
      files.foreach { f => targetContext.attachFile(f) }

      //    println("Resolved files: " + files)

    } catch {
      case e: ClassNotFoundException =>
        // TODO: Lift exception into domain
        throw e
    }

  }

}

