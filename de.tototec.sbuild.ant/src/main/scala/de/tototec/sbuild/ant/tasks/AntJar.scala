package de.tototec.sbuild.ant.tasks

import java.io.File
import org.apache.tools.ant.types.{ FileSet => AFileSet }
import org.apache.tools.ant.types.{ ZipFileSet => AZipFileSet }
import org.apache.tools.ant.types.spi.Service
import org.apache.tools.ant.taskdefs.Jar
import de.tototec.sbuild.ant.AntProject
import de.tototec.sbuild.Project
import org.apache.tools.ant.taskdefs.Manifest

object AntJar {
  def apply(destFile: File = null,
            baseDir: File = null,
            manifest: File = null,
            includes: String = null,
            excludes: String = null,
            // since SBUild 0.1.3.9000
            compress: java.lang.Boolean = null,
            keepCompression: java.lang.Boolean = null,
            filesOnly: java.lang.Boolean = null,
            metaInf: AZipFileSet = null,
            fileSet: AFileSet = null,
            fileSets: Seq[AFileSet] = null,
            manifestEntries: Map[String, String] = null,
            manifestSectionEntries: Map[String, Map[String, String]] = null,
            service: Service = null,
            services: Seq[Service] = null)(implicit _project: Project) =
    new AntJar(
      destFile = destFile,
      baseDir = baseDir,
      manifest = manifest,
      includes = includes,
      excludes = excludes,
      compress = compress,
      keepCompression = keepCompression,
      filesOnly = filesOnly,
      metaInf = metaInf,
      fileSet = fileSet,
      fileSets = fileSets,
      manifestEntries = manifestEntries,
      manifestSectionEntries = manifestSectionEntries,
      service = service,
      services = services
    ).execute
}

class AntJar()(implicit _project: Project) extends Jar {
  setProject(AntProject())
  //
  def this(destFile: File = null,
           baseDir: File = null,
           manifest: File = null,
           includes: String = null,
           excludes: String = null,
           // since SBUild 0.1.3.9000
           compress: java.lang.Boolean = null,
           keepCompression: java.lang.Boolean = null,
           filesOnly: java.lang.Boolean = null,
           metaInf: AZipFileSet = null,
           fileSet: AFileSet = null,
           fileSets: Seq[AFileSet] = null,
           manifestEntries: Map[String, String] = null,
           manifestSectionEntries: Map[String, Map[String, String]] = null,
           service: Service = null,
           services: Seq[Service] = null)(implicit _project: Project) {
    this
    if (destFile != null) setDestFile(destFile)
    if (baseDir != null) setBasedir(baseDir)
    if (manifest != null) setManifest(manifest)
    if (includes != null) setIncludes(includes)
    if (excludes != null) setExcludes(excludes)
    if (compress != null) setCompress(compress.booleanValue())
    if (keepCompression != null) setKeepCompression(keepCompression.booleanValue())
    if (filesOnly != null) setFilesonly(filesOnly.booleanValue())
    if (metaInf != null) addMetainf(metaInf)
    if (fileSet != null) addFileset(fileSet)
    if (fileSets != null && !fileSets.isEmpty) fileSets.foreach { fileSet =>
      addFileset(fileSet)
    }

    var _innerManifest: Option[Manifest] = None;
    def innerManifest: Manifest = {
      if (_innerManifest.isEmpty) _innerManifest = Some(new Manifest())
      _innerManifest.get;
    }

    if (manifestEntries != null) {
      manifestEntries.foreach {
        case (key, value) =>
          val attribute = new Manifest.Attribute()
          attribute.setName(key)
          attribute.setValue(value)
          innerManifest.addConfiguredAttribute(attribute)
      }
    }
    if (manifestSectionEntries != null) {
      manifestSectionEntries.foreach {
        case (sectionName, entries) if entries != null && !entries.isEmpty =>
          val section = new Manifest.Section()
          section.setName(sectionName)
          entries.foreach {
            case (key, value) =>
              val attribute = new Manifest.Attribute()
              attribute.setName(key)
              attribute.setValue(value)
              section.addConfiguredAttribute(attribute)
          }
          innerManifest.addConfiguredSection(section)
        case _ =>
      }
    }

    _innerManifest.map { addConfiguredManifest(_) };

    if (service != null) addConfiguredService(service)
    if (services != null) services.foreach {
      service => addConfiguredService(service)
    }

  }

  def setBaseDir(baseDir: File) = setBasedir(baseDir)
  def setFilesOnly(filesOnly: Boolean) = setFilesonly(filesOnly)

}