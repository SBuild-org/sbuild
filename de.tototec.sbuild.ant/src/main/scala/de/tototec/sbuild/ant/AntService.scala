package de.tototec.sbuild.ant

import org.apache.tools.ant.types.spi.Service
import de.tototec.sbuild.Project
import de.tototec.sbuild.LogLevel
import org.apache.tools.ant.types.spi.Provider

// Since 0.1.3.9000
object AntService {
  def apply(
    `type`: String = null,
    serviceType: String = null,
    provider: String = null,
    providers: Seq[String] = null,
    description: String = null)(implicit _project: Project) =
    new AntService(
      `type` = `type`,
      serviceType = serviceType,
      provider = provider,
      providers = providers,
      description = description
    )
}

// Since 0.1.3.9000
class AntService()(implicit _project: Project) extends Service {
  setProject(AntProject())

  def this(
    `type`: String = null,
    serviceType: String = null,
    provider: String = null,
    providers: Seq[String] = null,
    description: String = null)(implicit _project: Project) {
    this
    if (`type` != null && serviceType != null && `type` != serviceType) {
      // TODO Use level warn if available
      _project.log.log(LogLevel.Info, "Both parameter ('type' and 'serviceType') were given but differ in its value. Prefering parameter 'type'.")
    }
    if (serviceType != null) setType(serviceType)
    if (`type` != null) setType(`type`)
    if (provider != null) {
      val p = new Provider()
      p.setClassName(provider)
      addConfiguredProvider(p)
    }
    if (providers != null && !providers.isEmpty) {
      providers.foreach { provider =>
        val p = new Provider()
        p.setClassName(provider)
        addConfiguredProvider(p)
      }
    }
    if (description != null) setDescription(description)
  }

}