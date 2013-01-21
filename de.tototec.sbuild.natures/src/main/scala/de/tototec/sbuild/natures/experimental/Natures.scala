package de.tototec.sbuild.natures.experimental

import de.tototec.sbuild._
import de.tototec.sbuild.TargetRefs._
import de.tototec.sbuild.ant._
import de.tototec.sbuild.ant.tasks._

/**
 * Natures depends at least on SBuild 0.3.2 or above.
 *
 * To ensure, that it is always obvious where a def/val comes from, some naming policy is required.
 * Without, users would very fast have the feeling of magic and uncertainty.
 *
 * Also, as a best practice, when configuring your mixed natures,
 * you should always add the optional "overwrite" keyword when you intend to overwrite something.
 * That way, the compile will understand your intend and can give a meaningful error message,
 * if for some reason there is no such def to override.
 *
 * Naming policy: Each nature should only define new methods in its own namespace.
 * Example: The Nature "MyOwnNature" should create all def's with the prefix "myOwn_", the "Nature" suffix should not be part of it.
 * Of course, "myOwn" would be also ok as a name, if only one def is needed and the name is already self describing,
 * like e.g. "OutputDirNature" and the def "outputDir".
 *
 */
trait Nature {

  /**
   * Create target(s) in the scope of the given (implicit) project.
   * Any implementation has to take care of calling super.createTargets.
   */
  def createTargets: Seq[Target] = Seq()

}




