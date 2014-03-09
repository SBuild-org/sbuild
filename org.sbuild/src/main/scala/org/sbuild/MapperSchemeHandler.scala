package org.sbuild

import org.sbuild.SchemeHandler.SchemeContext
import org.sbuild.internal.I18n

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
            val i18n = I18n[MapperSchemeHandler]
            val msg = i18n.marktr("Cannot find a source scheme handler (mapping or translator) for \"{0}\".")
            throw new TargetNotFoundException(i18n.notr(msg, path), null, i18n.tr(msg, path))
        }
    }
  }

}