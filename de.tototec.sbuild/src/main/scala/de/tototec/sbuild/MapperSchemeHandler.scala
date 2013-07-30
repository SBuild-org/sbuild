package de.tototec.sbuild

import de.tototec.sbuild.SchemeHandler.SchemeContext

/**
 * A MapperSchemeHandler will maintain an mapping from on scheme to another scheme.
 */
class MapperSchemeHandler(
  schemeMapping: Seq[(String, String)] = Seq(),
  pathTranslators: Seq[(String, String => String)] = Seq())(implicit project: Project)
    extends SchemeHandler {

  override def localPath(schemeContext: SchemeContext): String = {
    val path = schemeContext.path
    schemeMapping.find { case (scheme, _) => path.startsWith(scheme + ":") } match {
      case Some((scheme, sourceScheme)) =>
        sourceScheme + path.substring(scheme.size, path.size)
      case None =>
        pathTranslators.find { case (scheme, _) => path.startsWith(scheme + ":") } match {
          case Some((scheme, pathTranslator)) =>
            scheme + ":" + pathTranslator(path.substring(scheme.size + 1, path.size))
          case None =>
            throw new TargetNotFoundException(s"""Cannot find a source scheme handler (mapping or translator) for "${path}".""")
        }
    }
  }

}