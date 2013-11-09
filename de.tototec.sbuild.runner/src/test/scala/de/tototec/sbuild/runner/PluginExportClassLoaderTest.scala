package de.tototec.sbuild.runner

import org.scalatest.FunSuite

class PluginClassLoaderTest extends FunSuite {

  val underTest = new PluginExportClassLoader(null, null) {
    override lazy val allowedPackageNames: Seq[String] = Seq("a.b.c", "a.b.c.d")
  }

  val cases = Seq(
    "a.b.c.Class" -> true,
    "a.b.c.Class.SubClass" -> true,
    "a.b.c.d.Class" -> true,
    "a.b.Class" -> false
  )

  cases.map {
    case (p, a) => test("Package " + p + " should be " + (if(a) "" else "not ") + "allowed") {
     assert( underTest.checkClassNameInExported(p) === a)
    }
  }

}