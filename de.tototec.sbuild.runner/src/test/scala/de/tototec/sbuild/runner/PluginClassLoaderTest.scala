package de.tototec.sbuild.runner

import org.scalatest.FunSuite

class PluginClassLoaderTest extends FunSuite {

  val pluginInfo = new LoadablePluginInfo(Seq(), true) {
    override lazy val exportedPackages: Option[Seq[String]] = Some(Seq("a.b.c", "a.b.c.d"))
  }

  val underTest = new PluginClassLoader(pluginInfo, null)
  val cases = Seq(
    "a.b.c.Class" -> true,
    "a.b.c.Class.SubClass" -> true,
    "a.b.c.d.Class" -> true,
    "a.b.Class" -> false
  )

  cases.map {
    case (p, a) => test("Package " + p + " should be " + (if (a) "" else "not ") + "allowed") {
      assert(underTest.checkClassNameInExported(p) === a)
    }
  }

}