package de.tototec.sbuild.runner

import org.scalatest.Finders
import org.scalatest.FreeSpec

class LoadablePluginInfoTest extends FreeSpec {

  "A plugin which exports packages: a.b.c, a.b.c.d" - {

    val pluginInfo = new LoadablePluginInfo(Seq(), true) {
      override val exportedPackages: Option[Seq[String]] = Some(Seq("a.b.c", "a.b.c.d"))
    }

    val cases = Seq(
      "a.b.c.Class" -> true,
      "a.b.c.Class.SubClass" -> true,
      "a.b.c.d.Class" -> true,
      "a.b.Class" -> false
    )

    cases.map {
      case (p, a) => "should " + (if (a) "" else "not ") + "export " + p in {
        assert(pluginInfo.checkClassNameInExported(p) === a)
      }
    }
  }

  "A plugin which exports packages: a.b.c, a.b.c.d, a.b.d.*" - {

    val pluginInfo = new LoadablePluginInfo(Seq(), false) {
      override val exportedPackages: Option[Seq[String]] = Some(Seq("a.b.c", "a.b.c.d", "a.b.d.*"))
    }

    val cases = Seq(
      "a.b.c.Class" -> true,
      "a.b.c.Class.SubClass" -> true,
      "a.b.c.d.Class" -> true,
      "a.b.Class" -> false,
      "a.b.d.Class" -> true,
      "a.b.d.e.Class" -> true,
      "a.b.d.e.f.Class" -> true   
    )

    cases.map {
      case (p, a) => "should " + (if (a) "" else "not ") + "export " + p in {
        assert(pluginInfo.checkClassNameInExported(p) === a)
      }
    }
  }

  "A plugin which exports packages: a.b.c, !a.b.c.d, !a.b.d.e, a.b.d.*" - {

    val pluginInfo = new LoadablePluginInfo(Seq(), false) {
      override val exportedPackages: Option[Seq[String]] = Some(Seq("a.b.c", "!a.b.c.d", "!a.b.d.e", "a.b.d.*"))
    }

    val cases = Seq(
      "a.b.c.Class" -> true,
      "a.b.c.Class.SubClass" -> true,
      "a.b.c.d.Class" -> false,
      "a.b.Class" -> false,
      "a.b.d.Class" -> true,
      "a.b.d.e.Class" -> false,
      "a.b.d.e.f.Class" -> true
    )

    cases.map {
      case (p, a) => "should " + (if (a) "" else "not ") + "export " + p in {
        assert(pluginInfo.checkClassNameInExported(p) === a)
      }
    }
  }

}