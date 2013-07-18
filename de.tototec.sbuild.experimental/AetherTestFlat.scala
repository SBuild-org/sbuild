import de.tototec.sbuild._
import de.tototec.sbuild.TargetRefs._
import de.tototec.sbuild.ant._
import de.tototec.sbuild.ant.tasks._

@version("0.5.0")
@classpath(
  "target/de.tototec.sbuild.experimental.aether-0.5.0.9000.jar",
  "target/de.tototec.sbuild.experimental.aether.impl-0.5.0.9000.jar",
  "mvn:org.eclipse.aether:aether-api:0.9.0.M2",
  "mvn:org.eclipse.aether:aether-spi:0.9.0.M2",
  "mvn:org.eclipse.aether:aether-util:0.9.0.M2",
  "mvn:org.eclipse.aether:aether-impl:0.9.0.M2",
  "mvn:org.eclipse.aether:aether-connector-file:0.9.0.M2",
  "mvn:org.eclipse.aether:aether-connector-asynchttpclient:0.9.0.M2",
  "mvn:org.eclipse.aether:aether-connector-wagon:0.9.0.M2",
  "mvn:io.tesla.maven:maven-aether-provider:3.1.2",
  "mvn:org.apache.maven.wagon:wagon-provider-api:2.4",
  "mvn:org.apache.maven.wagon:wagon-http:2.4",
  "mvn:org.apache.maven.wagon:wagon-file:2.4",
  "mvn:org.apache.maven.wagon:wagon-ssh:2.4",
  "mvn:org.sonatype.maven:wagon-ahc:1.2.1",
  "mvn:org.apache.maven.wagon:wagon-http-shared4:2.4",
  "mvn:org.codehaus.plexus:plexus-component-annotations:1.5.5",
  "mvn:org.apache.httpcomponents:httpclient:4.2.5",
  "mvn:org.apache.httpcomponents:httpcore:4.2.4",
  "mvn:javax.inject:javax.inject:1",
  "mvn:com.ning:async-http-client:1.6.5",
  "mvn:io.tesla.maven:maven-model:3.1.0",
  "mvn:io.tesla.maven:maven-model-builder:3.1.0",
  "mvn:io.tesla.maven:maven-repository-metadata:3.1.0",
  "mvn:org.jboss.netty:netty:3.2.5.Final",
  "mvn:org.eclipse.sisu:org.eclipse.sisu.inject:0.0.0.M1",
  "mvn:org.eclipse.sisu:org.eclipse.sisu.plexus:0.0.0.M1",
  "mvn:org.codehaus.plexus:plexus-classworlds:2.4",
  "mvn:org.codehaus.plexus:plexus-interpolation:1.16",
  "mvn:org.codehaus.plexus:plexus-utils:2.1",
  "mvn:org.sonatype.sisu:sisu-guava:0.9.9",
  "mvn:org.sonatype.sisu:sisu-guice:3.1.0",
  "mvn:org.slf4j:slf4j-api:1.7.5",
  "mvn:org.slf4j:slf4j-simple:1.7.5"
)
class AetherTestFlat(implicit _project: Project) {

  SchemeHandler("aether", new experimental.aether.AetherSchemeHandler())

  val testCp =
    "aether:" +
    "org.testng:testng:5.6;classifier=jdk15," +
    "ch.qos.logback:logback-classic:1.0.9"

  Target("phony:resolveViaAether") dependsOn testCp exec {
    println("Resolved classpath: " + testCp.files)
  }

  Target("phony:show") dependsOn Prop("deps", "") exec { ctx: TargetContext =>
    println("Deps:\n - " + Prop("deps").files.mkString("\n - "))
  }

}
