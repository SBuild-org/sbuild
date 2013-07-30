package de.tototec.sbuild.experimental.aether

import de.tototec.sbuild.Project
import de.tototec.sbuild.SchemeHandler.SchemeContext
import de.tototec.sbuild.SchemeResolver
import de.tototec.sbuild.TargetContext
import scala.collection.JavaConverters._
import de.tototec.sbuild.LogLevel
import java.net.URL
import de.tototec.sbuild.Util
import java.io.File
import java.io.FileOutputStream
import java.io.BufferedOutputStream

private object ClasspathUtil extends ClasspathUtil

private class ClasspathUtil {

  def extractResourceToFile(classLoader: ClassLoader, resource: String, allElements: Boolean, deleteOnVmExit: Boolean, project: Project): Seq[File] = {

    val resources = classLoader.getResources(resource)

    def save(url: URL): File = {

      val fileName = new File(url.getPath()).getName()

      val resStream = url.openStream()

      val tmpFile = File.createTempFile("$$$", fileName)
      if (deleteOnVmExit) tmpFile.deleteOnExit
      val outStream = new BufferedOutputStream(new FileOutputStream(tmpFile))

      try {
        project.log.log(LogLevel.Debug, "About to extract classpath resource info file: " + tmpFile)
        Util.copy(resStream, outStream)

      } finally {
        outStream.close
        resStream.close
      }

      tmpFile
    }

    project.log.log(LogLevel.Debug, s"About to find ${if (allElements) "all" else "the first"} matching classpath resources: ${resource}")
    if (allElements)
      resources.asScala.toSeq.map(save(_))
    else if (resources.hasMoreElements)
      Seq(save(resources.nextElement))
    else Seq()
  }

}