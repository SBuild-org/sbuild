package de.tototec.sbuild.internal

import scala.annotation.tailrec
import de.tototec.sbuild.Logger
import de.tototec.sbuild.ProjectConfigurationException

class DependentClassesOrderer {

  private[this] val log = Logger[DependentClassesOrderer]

  def orderClasses(classes: Seq[Class[_]], dependencies: Seq[(Class[_], Class[_])]): Seq[Class[_]] = {
    var unchained: Seq[Class[_]] = classes
    var chained: Seq[Class[_]] = Seq()
    log.debug(s"Trying to order plugins: ${unchained}")

    def hasNoDeps(plugin: Class[_]): Boolean = dependencies.filter { case (a, b) => a == plugin && unchained.contains(b) }.isEmpty

    @tailrec
    def searchNextResolved(candidates: Seq[Class[_]]): Option[Class[_]] = candidates match {
      case Seq() => None
      case head +: tail => if (hasNoDeps(head)) Some(head) else searchNextResolved(tail)
    }

    while (!unchained.isEmpty) {
      log.debug(s"aleady chained: ${chained}")
      log.debug(s"still needs chaining: ${unchained}")
      searchNextResolved(unchained) match {
        case None => throw new ProjectConfigurationException("Could not resolve inter plugin dependencies")
        case Some(c) =>
          //            val c = c_.asInstanceOf[Class[_]]
          val unchainedSize = unchained.size
          log.debug(s"chaining plugin: ${c} with id: ${System.identityHashCode(c)}")
          chained ++= Seq(c)
          unchained = unchained.filter(_ != c)
          require(unchainedSize > unchained.size, "Unchained plugins must shrink after one plugin is chained")
      }
    }

    chained
  }

}