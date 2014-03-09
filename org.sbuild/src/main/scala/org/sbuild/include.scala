package org.sbuild

import scala.annotation.Annotation

/**
 * Include one ore more Scala files to access them from the current project.
 * The included files will be automatically compiled and added to the current classpath of the project.
 *
 * The `@include` annotation has only effect when used in a build file. 
 * Additional compile and runtime dependencies of the included file can be added with the [[org.sbuild.classpath @classpath annotation]]. 
 *
 * The `@include` annotation supports local files and all default scheme handlers.
 *
 * Example of a build file with an included file:
 *
 * {{{
 * import org.sbuild._
 * 
 * @include("../SharedConfig.scala")
 * class SBuild(implicit _project: Project) {
 *   // ...
 * }
 * }}}
 *
 * Classes and objects from included files may refer to their current directory, e.g. to access file system resources.
 * This can be easily accomplished by using [[org.sbuild.Path$.apply[T]*]].
 *
 * Example of an included file, utilizing [[org.sbuild.Path$.apply[T]*]]:
 * {{{
 * // ... in an included file
 *
 * object SharedConfig {
 *
 *   val version = "1.0.0"
 *
 *   def productLogoFile(implicit _project: Project) = Path[SharedConfig.type]("images/logo.jpg")
 *
 * }
 * }}}
 *
 * @param value One or more resources to be included.
 *   All default scheme handlers are supported.
 *
 * @see [[org.sbuild.classpath @classpath Annotation]]
 * @see [[org.sbuild.Path$ Path]]
 */
class include(value: String*) extends Annotation 
