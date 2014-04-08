package org.sbuild.runner

import java.io.File
import java.net.URL
import java.util.jar.JarInputStream

import org.sbuild.Constants
import org.sbuild.Logger
import org.sbuild.ProjectConfigurationException

object LoadablePluginInfo {
  /**
   * Information used to load and technically describle an SBuild plugin.
   */
  case class PluginClasses(instanceClass: String, factoryClass: String, version: String) {
    def name: String = s"${instanceClass}-${version}"
  }
}

/**
 * Encapsulation of a loadable SBuild plugin.
 * Given a set of files, this information gathered by this class can be used to load an SBuild plugin.
 *
 * @see PluginClassLoader
 */
class LoadablePluginInfo(val files: Seq[File], raw: Boolean) {

  import LoadablePluginInfo._

  private[this] val log = Logger[LoadablePluginInfo]

  lazy val urls: Seq[URL] = files.map(_.toURI().toURL())

  val (
    exportedPackages: Option[Seq[String]],
    dependencies: Seq[String],
    // (instanceClassName, factoryClassName, version)
    pluginClasses: Seq[PluginClasses],
    sbuildVersion: Option[String]
    ) = if (raw || files.isEmpty) (None, Seq(), Seq(), None) else {

    // TODO: Support more than one url, see also https://github.com/SBuild-org/sbuild/issues/175
    val manifest = Option(new JarInputStream(urls.head.openStream()).getManifest())

    val exportedPackages: Option[Seq[String]] = manifest.flatMap { m =>
      m.getMainAttributes().getValue(Constants.ManifestSBuildExportPackage) match {
        case null => None
        //      log.warn("Plugin does not define Manifest Entry " + Constants.SBuildPluginExportPackage)
        //      Seq()
        case v => Some(v.split(",").map(_.trim))
      }
    }

    val dependencies: Seq[String] = manifest.toSeq.flatMap { m =>
      m.getMainAttributes().getValue(Constants.ManifestSBuildClasspath) match {
        case null => Seq()
        case c =>
          // TODO, support more featureful splitter, because we want to support the whole schemes
          c.split(",").toSeq.map(_.trim)
      }
    }

    val pluginClasses: Seq[PluginClasses] = manifest.toSeq.flatMap { m =>
      m.getMainAttributes().getValue(Constants.ManifestSBuildPlugin) match {
        case null => Seq()
        case p =>
          p.split(",").toSeq.map { entry =>
            entry.split("=", 2) match {
              case Array(instanceClassName, factoryClassNameAndVersoin) =>
                val fnv = factoryClassNameAndVersoin.split(";version=", 2)
                val factoryClassName = fnv(0)
                val version =
                  if (fnv.size == 1) "0.0.0"
                  else {
                    val versionString = fnv(1).trim
                    if (versionString.startsWith("\"") && versionString.endsWith("\"")) {
                      versionString.substring(1, versionString.size - 1)
                    } else versionString
                  }
                PluginClasses(instanceClassName.trim, factoryClassName.trim, version)
              case _ =>
                // FIXME: Change exception to new plugin exception
                val ex = new ProjectConfigurationException("Invalid plugin entry: " + entry)
                throw ex
            }
          }
      }

    }

    val sbuildVersion = manifest.flatMap { m =>
      m.getMainAttributes().getValue(Constants.ManifestSBuildVersion) match {
        case null => None
        case v => Some(v.trim)
      }
    }

    (exportedPackages, dependencies, pluginClasses, sbuildVersion)
  }

  def checkClassNameInExported(className: String): Boolean = exportedPackages match {
    case None => true
    case Some(ep) =>
      val parts = className.split("\\.").toList.reverse

      def removeNonPackageParts(parts: List[String]): List[String] = parts match {
        case cn :: p if (cn.headOption.filter(_.isLower).isEmpty) => removeNonPackageParts(p)
        case p => p
      }
      val packageName = removeNonPackageParts(parts.tail).reverse.mkString(".")
      ep.toIterator.map { export =>
        def matches(packageName: String): Boolean = (export == packageName ||
          (export.endsWith(".*") && packageName.startsWith(export.substring(0, export.length - 2))) ||
          (export.endsWith("*") && packageName.startsWith(export.substring(0, export.length - 1))))

        if (matches(packageName)) Some(true)
        else if (matches("!" + packageName)) Some(false)
        else None
      }.find(_.isDefined) match {
        case Some(Some(exported)) => exported
        case _ => false
      }
  }

  override def toString() = getClass.getSimpleName +
    "(files=" + files +
    ",raw=" + raw +
    ")"

}
