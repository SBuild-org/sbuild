package de.tototec.sbuild.experimental

import java.io.File
import org.apache.maven.repository.internal.MavenRepositorySystemUtils
import org.apache.maven.wagon.Wagon
import org.apache.maven.wagon.providers.file.FileWagon
import org.apache.maven.wagon.providers.http.HttpWagon
import org.eclipse.aether.RepositorySystem
import org.eclipse.aether.artifact.DefaultArtifact
import org.eclipse.aether.collection.CollectRequest
import org.eclipse.aether.connector.wagon.WagonProvider
import org.eclipse.aether.connector.wagon.WagonRepositoryConnectorFactory
import org.eclipse.aether.graph.Dependency
import org.eclipse.aether.repository.LocalRepository
import org.eclipse.aether.repository.RemoteRepository
import org.eclipse.aether.resolution.DependencyRequest
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory
import org.eclipse.aether.util.graph.visitor.PreorderNodeListGenerator
import de.tototec.sbuild.MavenSupport.MavenGav
import de.tototec.sbuild.SchemeResolver
import de.tototec.sbuild.TargetContext
import org.sonatype.maven.wagon.AhcWagon
import scala.collection.JavaConverters._
import org.eclipse.aether.connector.file.FileRepositoryConnectorFactory

trait AetherContext {
}

object AetherSchemeHandler {

  class ManualWagonProvider extends WagonProvider {
    override def lookup(roleHint: String): Wagon = roleHint match {
      case "http" => new AhcWagon()
      // case "file" => new FileWagon()
      // case "scp" => new ScpWagon()
      // case "sftp" => new SftpWagon()
      case _ => null
    }
    override def release(wagon: Wagon) {}
  }

}

class AetherSchemeHandler(aetherContext: AetherContext = new AetherContext {}) extends SchemeResolver {
  import AetherSchemeHandler._

  def localPath(path: String): String = s"phony:maven-${path}-dependencies"

  def resolve(path: String, targetContext: TargetContext) {

    val localRepoDir = new File(System.getProperty("user.home") + "/.m2/repository")

    def newRepositorySystem() = {
      val locator = MavenRepositorySystemUtils.newServiceLocator()
      locator.setServices(classOf[WagonProvider], new ManualWagonProvider())
      locator.setService(classOf[RepositoryConnectorFactory], classOf[FileRepositoryConnectorFactory])
      locator.setService(classOf[RepositoryConnectorFactory], classOf[WagonRepositoryConnectorFactory])
      val system = locator.getService(classOf[RepositorySystem])
      assert(system != null)
      system
    }

    def newSession(system: RepositorySystem) = {
      val session = MavenRepositorySystemUtils.newSession()
      val localRepo = new LocalRepository(localRepoDir)
      session.setLocalRepositoryManager(system.newLocalRepositoryManager(session, localRepo))
      session
    }

    val repoSystem = newRepositorySystem()
    val session = newSession(repoSystem)

    // somehow extract artifact info for given deps
    val requestedArtifacts: Seq[MavenGav] = List()
    // create Maven dependencies from it
    val deps = requestedArtifacts.map {
      case MavenGav(a, g, v, classifier) =>
        new Dependency(new DefaultArtifact(s"$a:$g$v"), "compile")
    }
    val dep = deps.head

    val centralRepo = new RemoteRepository.Builder("central", "default", "http://repo1.maven.org/maven2").build()

    val collectRequest = new CollectRequest()
    deps match {
      case Nil =>
      case root :: other =>
        collectRequest.setRoot(root)
        other.foreach(d => collectRequest.addDependency(d))
    }
    collectRequest.addRepository(centralRepo)
    val node = repoSystem.collectDependencies(session, collectRequest).getRoot()

    val dependencyRequest = new DependencyRequest()
    dependencyRequest.setRoot(node)

    repoSystem.resolveDependencies(session, dependencyRequest)

    val nlg = new PreorderNodeListGenerator()
    node.accept(nlg)

    val files = nlg.getNodes().asScala.map { n =>
      val dep = n.getDependency()
      val artifact = if (dep != null) dep.getArtifact() else null
      val file = if (artifact != null) artifact.getFile else null
      if (file != null) file.getAbsoluteFile() else null
    }.filter(_ != null)

    //    val classpath = nlg.getClassPath()
    //    System.out.println(nlg.getClassPath())

    println("Dependency files: " + files)

  }

}

