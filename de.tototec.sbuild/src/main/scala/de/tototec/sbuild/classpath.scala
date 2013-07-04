package de.tototec.sbuild

import scala.annotation.Annotation

/**
 * Add additional classpath elements (jars, dirs) to the compile and execution environment of the current build file.
 * This is required to used foreign libraries and APIs in your build script,
 * otherwise you would be limited to the standard API of the Java, Scala and SBuild distribution.
 *
 * The `@classpath` annotation has only effect when used in a build file.
 *
 * The `@classpath` annotation supports local files and all default scheme handlers.
 *
 * Example of a build file which uses two additional classpath resources, one via the "mvn" scheme handler and one local file:
 * {{{
 * import de.tototec.sbuild,_
 *
 * @classpath(
 *   "mvn:org.apache.ant:ant:1.8.4",
 *   "../common/buildtasks.jar"
 * )
 * class SBuild(implicit _project: Project) {
 *
 * }
 *
 * }}}
 *
 * @param value One or more resources to be included into the classpath.
 *   All default scheme handlers are supported.
 *
 * @see [[de.tototec.sbuild.include @include]]
 */
class classpath(value: String*) extends Annotation 