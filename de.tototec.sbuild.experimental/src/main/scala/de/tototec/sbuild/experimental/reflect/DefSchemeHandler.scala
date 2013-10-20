package de.tototec.sbuild.experimental.reflect

import java.io.File
import java.lang.reflect.Field
import java.lang.reflect.Method

import scala.util.Success
import scala.util.Try

import de.tototec.sbuild.Logger
import de.tototec.sbuild.Project
import de.tototec.sbuild.SchemeHandler.SchemeContext
import de.tototec.sbuild.SchemeResolverWithDependencies
import de.tototec.sbuild.TargetContext
import de.tototec.sbuild.TargetNotFoundException
import de.tototec.sbuild.TargetRef
import de.tototec.sbuild.TargetRefs

/**
 * Access to build script variables of type [[TargetRefs]], [[TargetRef]], [[java.io.File]], [[java.lang.String]] as targets.
 */
class DefSchemeHandler(scriptInstance: Any)(implicit _project: Project) extends SchemeResolverWithDependencies {

  private[this] val log = Logger[DefSchemeHandler]

  override def localPath(schemeCtx: SchemeContext): String = s"phony:${schemeCtx.scheme}:${schemeCtx.path}"

  def resolve(schemeCtx: SchemeContext, targetContext: TargetContext) {} // Noop

  override def dependsOn(schemeCtx: SchemeContext): TargetRefs = {
    val access = Try(scriptInstance.getClass.getMethod(schemeCtx.path)).orElse(Try(scriptInstance.getClass.getField(schemeCtx.path)))
    log.debug("Accessor for \"" + schemeCtx.path + "\" is: " + access)
    val value = access match {
      case Success(field: Field) => field.get(scriptInstance)
      case Success(method: Method) => method.invoke(scriptInstance)
      case _ =>
        val ex = new TargetNotFoundException("Cannot find field or method with name \"" + schemeCtx.path + "\" in project script")
        ex.buildScript = Some(_project.projectFile)
        throw ex
    }
    val targetRefs = value match {
      case x: TargetRefs => x
      case x: TargetRef => TargetRefs(x)
      case x: File => TargetRefs.fromFile(x)
      case x: String => TargetRefs.fromString(x)
      case x =>
        val ex = new TargetNotFoundException("Cannot access field or method with name \"" + schemeCtx.path + "\" in project script. Value was null or has unsupported type: " + (if (x == null) "null" else x.getClass()))
        ex.buildScript = Some(_project.projectFile)
        throw ex
    }
    log.debug("Evaluated dependsOn to: " + targetRefs)
    targetRefs
  }

}
