package org.sbuild.internal

import java.io.File
import java.io.FilenameFilter
import org.sbuild.SchemeHandler
import org.sbuild.TargetContext
import org.sbuild.TargetNotFoundException
import org.sbuild.TransparentSchemeResolver
import org.sbuild.Prop

/**
 * This SchemeHandler provides a "sbuild:" scheme, to provide some internal pseudo dependencies.
 *
 *
 */
class SBuildSchemeHandler(projectLastModified: Long, cacheBaseDir: File)
    extends SchemeHandler
    with TransparentSchemeResolver {

  private[this] val propBaseDir = new File(cacheBaseDir, "prop")

  override def localPath(schemeContext: SchemeHandler.SchemeContext): String =
    schemeContext.path match {
      case "project" | "force" => s"phony:${schemeContext.fullName}"
      case prop if prop.startsWith("prop=") => s"phony:${schemeContext.fullName}"
      case _ =>
        throw new TargetNotFoundException("Unsupported path in dependency: " + schemeContext.fullName)
    }

  override def resolve(schemeContext: SchemeHandler.SchemeContext, targetContext: TargetContext): Unit = {
    schemeContext.path match {

      case "project" =>
        targetContext.targetLastModified = projectLastModified

      case "force" =>
        targetContext.targetLastModified = System.currentTimeMillis

      case prop if prop.startsWith("prop=") =>
        val propName = prop.substring("prop=".length)
        val checksum = propName
        val stateFile = findCreateAndCleanStateFile(propName, Prop.get(propName)(targetContext.project))
        targetContext.targetLastModified = stateFile.lastModified

      case _ =>
        throw new TargetNotFoundException("Unsupported path in dependency: " + schemeContext.fullName)
    }
  }

  def findCreateAndCleanStateFile(propName: String, propContent: Option[String]): File = {
    val propNameSum = Md5.md5sum(propName).substring(0, 10)
    val propFileName = propContent.map(prop => s"${propNameSum}_${Md5.md5sum(prop).substring(0, 10)}").getOrElse(propNameSum)
    val propFile = new File(propBaseDir, propFileName)

    if (!propFile.exists()) {
      propBaseDir.mkdirs
      // find old propfiles and delete them
      propBaseDir.listFiles(new FilenameFilter {
        override def accept(dir: File, name: String): Boolean = name.startsWith(propNameSum)
      }).map(_.delete)
      // create new file
      propFile.createNewFile()
    }

    propFile
  }

}