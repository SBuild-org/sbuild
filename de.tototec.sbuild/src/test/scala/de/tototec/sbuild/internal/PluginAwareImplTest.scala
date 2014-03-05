package de.tototec.sbuild.internal

import org.scalatest.FreeSpec
import de.tototec.sbuild.test.TestSupport
import de.tototec.sbuild.Plugin

class PluginAwareImplTest extends FreeSpec {

  "Plugin API" - {

    "Retrieve the version of a registered plugin" in {

      case class Fake()
      class FakePlugin extends Plugin[Fake] {
        override def applyToProject(instances: Seq[(String, Fake)]): Unit = ???
        def create(name: String): Fake = ???
      }

      implicit val p = TestSupport.createMainProject
      val pluginProject = p.asInstanceOf[PluginAwareImpl]
      pluginProject.registerPlugin(classOf[Fake].getName, classOf[FakePlugin].getName, "1.2.3", classOf[Fake].getClassLoader)
      assert(Plugin.version[Fake] === Some("1.2.3"))

      case class Fake2()
      assert(Plugin.version[Fake2] === None)

    }

  }

  "PluginHandle API" - {

  }

}