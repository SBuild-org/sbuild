package de.tototec.sbuild

import java.lang.reflect.Field
import java.lang.reflect.Method
import java.io.File
import scala.util.Try
import scala.util.Success

/**
 * Access to build script variables of type [[TargetRefs]], [[TargetRef]], [[java.io.File]], [[java.lang.String]] as targets.
 */
class DefSchemeHandler(scriptInstance: Any)(implicit _project: Project) extends SchemeResolverWithDependencies {

  override def localPath(path: String): String = "phony:" + path + "Field"

  def resolve(path: String, targetContext: TargetContext) {} // Noop

  override def dependsOn(path: String): TargetRefs = {
    val access = Try(scriptInstance.getClass.getMethod(path)).orElse(Try(scriptInstance.getClass.getField(path)))
    _project.log.log(LogLevel.Debug, "Accessor for \"" + path + "\" is: " + access)
    val value = access match {
      case Success(field: Field) => field.get(scriptInstance)
      case Success(method: Method) => method.invoke(scriptInstance)
      case _ =>
        val ex = new TargetNotFoundException("Cannot find field or method with name \"" + path + "\" in project script")
        ex.buildScript = Some(_project.projectFile)
        throw ex
    }
    val targetRefs = value match {
      case x: TargetRefs => x
      case x: TargetRef => TargetRefs(x)
      case x: File => TargetRefs.fromFile(x)
      case x: String => TargetRefs.fromString(x)
      case x =>
        val ex = new TargetNotFoundException("Cannot access field or method with name \"" + path + "\" in project script. Value was null or has unsupported type: " + (if (x == null) "null" else x.getClass()))
        ex.buildScript = Some(_project.projectFile)
        throw ex
    }
    _project.log.log(LogLevel.Debug, "Evaluated dependsOn to: " + targetRefs)
    targetRefs
  }

}