package de.tototec.sbuild.eclipse.plugin

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.URLClassLoader
import java.util.zip.ZipFile
import scala.collection.JavaConversions._
import scala.xml.XML
import de.tototec.sbuild.SBuildException
import de.tototec.sbuild.TargetRef
import de.tototec.sbuild.ProjectReader
import de.tototec.sbuild.SBuildVersion
import de.tototec.sbuild.Project
import de.tototec.sbuild.runner.Config
import de.tototec.sbuild.runner.SBuildRunner
import de.tototec.sbuild.runner.SimpleProjectReader
import org.eclipse.core.runtime.IPath
import org.eclipse.jdt.core.JavaCore
import de.tototec.sbuild.eclipse.plugin.internal.SBuildClasspathActivator
import java.net.URL
import de.tototec.sbuild.runner.ClasspathConfig

trait SBuildClasspathProjectReader {
  def buildFile: File
  def readResolveActions: Seq[ResolveAction]
}

object SBuildClasspathProjectReader {
  def load(sbuildHomeDir: File, settings: Settings, projectRootFile: File): SBuildClasspathProjectReader = {
    // Idea: load the byte stream and save it to .sbuild/eclipse/...
    // Create classloader with SBuild-jars AND .sbuild/eclipse

    val readerLibDir = new File(projectRootFile, ".sbuild/eclipse")
    val destDir = new File(readerLibDir, "de/tototec/sbuild/eclipse/plugin")
    destDir.mkdirs

    val readerImplClassName = "de.tototec.sbuild.eclipse.plugin.SBuildClasspathProjectReaderImpl"

    val activator = SBuildClasspathActivator.activator
    val bundleContext = activator.bundleContext
    val bundle = bundleContext.getBundle
    val prefix = "OSGI-INF/projectReaderLib/"
    val relevantClassURLs = bundle.findEntries(
      prefix + "de/tototec/sbuild/eclipse/plugin", "SBuildClasspathProjectReaderImpl*.class", false)

    // copy class files into project
    relevantClassURLs match {
      case null =>
        throw new RuntimeException("Could not found classfile(s) for de.tototec.sbuild.eclipse.plugin.SBuildClasspathProjectReaderImpl")
      case x =>
        def copyStream(sourceUrl: URL, outputFile: File) {
          val in = sourceUrl.openStream
          val out = new FileOutputStream(outputFile)
          try {
            val buf = new Array[Byte](1024)
            var len = 0
            while ({
              len = in.read(buf)
              len > 0
            }) {
              out.write(buf, 0, len)
            }
          } finally {
            if (out != null) {
              out.close
            }
          }
        }

        x.foreach {
          case url: URL =>
            val name = url.getPath.substring(prefix.length)
            val targetFile = new File(readerLibDir, name)
            copyStream(url, targetFile)
        }
    }

    val classpathes = Classpathes.fromFile(new File(sbuildHomeDir, "lib/classpath.properties"))

    val sbuildClassloader = new URLClassLoader(
      Array(readerLibDir.toURI().toURL()) ++
        classpathes.sbuildClasspath.map { path =>
          new File(path).toURI.toURL
        },
      getClass.getClassLoader
    )
    //    {
    //      override protected def loadClass(name: String, resolve: Boolean): Class[_] = {
    //        val parent: ClassLoader = getClass.getClassLoader
    //        val noParent = name == readerImplClassName
    //
    //        val loadedClass = findLoadedClass(name) match {
    //          // not loaded, but special loading required
    //          case null if noParent => findClass(name)
    //          // not loaded, but first try parent, then us
    //          case null => try {
    //            parent.loadClass(name)
    //          } catch {
    //            case e: ClassNotFoundException => findClass(name)
    //          }
    //          // already loaded
    //          case x => x
    //        }
    //
    //        if (resolve) {
    //          resolveClass(loadedClass)
    //        }
    //
    //        loadedClass
    //      }
    //    }

    val readerClass = sbuildClassloader.loadClass(readerImplClassName);
    val readerClassCtr = readerClass.getConstructor(classOf[Settings], classOf[File])
    val reader = readerClassCtr.newInstance(settings, projectRootFile);

    reader.asInstanceOf[SBuildClasspathProjectReader]
  }
}

class SBuildClasspathProjectReaderImpl(settings: Settings, projectRootFile: File) extends SBuildClasspathProjectReader {

  private val config = new Config()
  config.verbose = true
  config.buildfile = settings.sbuildFile

  override val buildFile = new File(projectRootFile, config.buildfile)

  override def readResolveActions: Seq[ResolveAction] = {

    val sbuildHomePath: IPath = JavaCore.getClasspathVariable(SBuildClasspathContainer.SBuildHomeVariableName)
    if (sbuildHomePath == null) {
      throw new RuntimeException("Classpath variable 'SBUILD_HOME' not defined")
    }
    val sbuildHomeDir = sbuildHomePath.toFile
    debug("Trying to use SBuild " + SBuildVersion.version + " installed at: " + sbuildHomeDir)

    val classpathConfig = new ClasspathConfig
    classpathConfig.sbuildHomeDir = sbuildHomeDir

    debug("About to read project")
    val projectReader: ProjectReader = new SimpleProjectReader(config, classpathConfig)

    implicit val sbuildProject = new Project(buildFile, projectReader)
    config.defines foreach {
      case (key, value) => sbuildProject.addProperty(key, value)
    }

    debug("About to read SBuild project: " + buildFile);
    try {
      projectReader.readProject(sbuildProject, buildFile)
    } catch {
      case e: Throwable =>
        debug("Could not read Project file. Cause: " + e.getMessage)
        throw e
    }

    val depsXmlString = sbuildProject.properties.getOrElse(settings.exportedClasspath, "<deps></deps>")
    debug("Determine Eclipse classpath by evaluating '" + settings.exportedClasspath + "' to: " + depsXmlString)
    val depsXml = XML.loadString(depsXmlString)

    val deps: Seq[String] = (depsXml \ "dep") map {
      depXml => depXml.text
    }

    var resolveActions = Seq[ResolveAction]()

    val depsAsTargetRefs = deps.map(TargetRef(_))

    depsAsTargetRefs.foreach { targetRef =>

      sbuildProject.findTarget(targetRef) match {
        case Some(target) =>
          // we have a target for this, so we need to resolve it, when required
          def action: Boolean = try {
            SBuildRunner.preorderedDependencies(request = List(target))
            true
          } catch {
            case e: SBuildException =>
              debug("Could not resolve dependency: " + target)
              false
          }
          resolveActions = resolveActions ++ Seq(ResolveAction(target.file.getPath, targetRef.ref, action _))
        case None =>
          targetRef.explicitProto match {
            case None | Some("file") =>
              // this is a file, so we need simply to add it to the classpath
              // but first, we check that it is absolute or if not, we make it absolute (based on their project)
              val file = new File(targetRef.name) match {
                case f if f.isAbsolute => f
                case _ => targetRef.explicitProject match {
                  case None => new File(projectRootFile, targetRef.name)
                  case Some(projFile: File) if projFile.isFile => new File(projFile.getParentFile, targetRef.name)
                  case Some(projDir: File) => new File(projDir, targetRef.name)
                }
              }
              resolveActions = resolveActions ++ Seq(ResolveAction(file.getPath, targetRef.ref, file.exists _))
            case Some("phony") =>
              // This is a phony target, we will ignore it for now
              debug("Ignoring phony target: " + targetRef)
            case _ =>
              // A scheme we might have a scheme handler for
              try {
                val target = sbuildProject.createTarget(targetRef)
                def action: Boolean = try {
                  SBuildRunner.preorderedDependencies(request = List(target))
                  true
                } catch {
                  case e: SBuildException =>
                    debug("Could not resolve dependency: " + target)
                    false
                }
                resolveActions = resolveActions ++ Seq(ResolveAction(target.file.getPath, targetRef.ref, action _))

              } catch {
                case e: SBuildException => debug("Could not resolve dependency: " + targetRef + ". Reason: " + e.getMessage)
              }
          }
      }
    }

    resolveActions
  }

  def unzip(archive: File, targetDir: File, selectedFiles: String*) {
    unzip(archive, targetDir, selectedFiles.map(f => (f, null)).toList)
  }

  def unzip(archive: File, targetDir: File, _selectedFiles: List[(String, File)]) {

    if (!archive.exists || !archive.isFile) throw new RuntimeException("Zip file cannot be found: " + archive);
    targetDir.mkdirs

    debug("Extracting zip archive '" + archive + "' to: " + targetDir)

    var selectedFiles = _selectedFiles
    val partial = !selectedFiles.isEmpty
    if (partial) debug("Only extracting some content of zip file")

    try {
      val zip = new ZipFile(archive)
      val entries = zip.entries
      while (entries.hasMoreElements && (!partial || !selectedFiles.isEmpty)) {
        val zipEntry = entries.nextElement

        val extractFile: Option[File] = if (partial) {
          if (!zipEntry.isDirectory) {
            val candidate = selectedFiles.find { case (name, _) => name == zipEntry.getName }
            if (candidate.isDefined) {
              selectedFiles = selectedFiles.filterNot(_ == candidate.get)
              if (candidate.get._2 != null) {
                Some(candidate.get._2)
              } else {
                val full = zipEntry.getName
                val index = full.lastIndexOf("/")
                val name = if (index < 0) full else full.substring(index)
                Some(new File(targetDir + "/" + name))
              }
            } else {
              None
            }
          } else {
            None
          }
        } else {
          if (zipEntry.isDirectory) {
            debug("  Creating " + zipEntry.getName);
            new File(targetDir + "/" + zipEntry.getName).mkdirs
            None
          } else {
            Some(new File(targetDir + "/" + zipEntry.getName))
          }
        }

        if (extractFile.isDefined) {
          debug("  Extracting " + zipEntry.getName);
          val targetFile = extractFile.get
          if (targetFile.exists
            && !targetFile.getParentFile.isDirectory) {
            throw new RuntimeException(
              "Expected directory is a file. Cannot extract zip content: "
                + zipEntry.getName());
          }
          // Ensure, that the directory exixts
          targetFile.getParentFile.mkdirs
          val outputStream = new BufferedOutputStream(new FileOutputStream(targetFile))
          val inputStream = zip.getInputStream(zipEntry)
          copy(inputStream, outputStream);
          outputStream.close
          inputStream.close
          if (zipEntry.getTime() > 0) {
            targetFile.setLastModified(zipEntry.getTime)
          }
        }
      }
    } catch {
      case e: IOException =>
        throw new RuntimeException("Could not unzip file: " + archive,
          e)
    }
  }

  private def copy(in: InputStream, out: OutputStream) {
    val buf = new Array[Byte](1024)
    var len = 0
    while ({
      len = in.read(buf)
      len > 0
    }) {
      out.write(buf, 0, len)
    }
  }

}
