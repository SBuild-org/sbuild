package org.sbuild.internal

import org.scalatest.FreeSpecLike
import org.sbuild.ProjectConfigurationException

class DependentClassesOrdererTest extends DependentClassesOrderer with FreeSpecLike {

  class C0
  class C1
  class C2
  class C3
  val c = Seq(classOf[C0], classOf[C1], classOf[C2], classOf[C3])

  "self-dependency should be caught" in {
    intercept[ProjectConfigurationException] {
      orderClasses(Seq(c(0)), Seq(c(0) -> c(0)))
    }
  }

  "cycle should be caught" in {
    intercept[ProjectConfigurationException] {
      orderClasses(Seq(c(0), c(1)), Seq(c(0) -> c(1), c(1) -> c(0)))
    }
  }

  "Original ordering should be keept if possible" - {
    class C0
    class C1
    class C2
    class C3

    case class Case(no: Int, c: Seq[Class[_]], d: Seq[(Class[_], Class[_])], r: Seq[Class[_]])
    val cases = Seq(
      Case(
        no = 1,
        c = Seq(c(0), c(1), c(2), c(3)),
        d = Seq(c(0) -> c(1), c(2) -> c(3)),
        r = Seq(c(1), c(0), c(3), c(2))
      ),
      Case(
        no = 2,
        c = Seq(c(0), c(1), c(2), c(3)),
        d = Seq(c(0) -> c(1), c(1) -> c(2), c(2) -> c(3)),
        r = Seq(c(3), c(2), c(1), c(0))
      ),
      Case(
        no = 3,
        c = Seq(c(0), c(1), c(2), c(3)),
        d = Seq(c(3) -> c(1), c(2) -> c(3)),
        r = Seq(c(0), c(1), c(3), c(2))
      )
    )

    cases.foreach { c =>
      s"correctly order case ${c.no}" in {
        assert(orderClasses(c.c, c.d) === c.r)
      }
    }
  }

}