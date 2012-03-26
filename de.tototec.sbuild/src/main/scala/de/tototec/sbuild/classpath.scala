package de.tototec.sbuild

import scala.annotation.Annotation

/**
 * Use this annotation to add additional classpath items to the execution environment of the current build file.
 * 
 * TODO: also add a @include annotation to specify source files to be included and compiled
 */
class classpath(value: String*) extends Annotation 