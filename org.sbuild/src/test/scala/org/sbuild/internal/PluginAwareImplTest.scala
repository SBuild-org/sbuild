package org.sbuild.internal

import org.scalatest.FreeSpec
import org.sbuild.test.TestSupport
import org.sbuild.Plugin
import org.sbuild.PluginWithDependencies
import org.sbuild.PluginDependency
import org.sbuild.ProjectConfigurationException

object PluginAwareImplTest {

  abstract class StubPlugin[T](i: T) extends Plugin[T] {
    override def applyToProject(instances: Seq[(String, T)]): Unit = {}
    override def create(name: String): T = i
  }

  case class Fake()
  class FakePlugin() extends StubPlugin[Fake](Fake())

  case class P1()
  class P1Plugin() extends StubPlugin[P1](P1())
  case class P2DependsOnP1()
  class P2DependsOnP1Plugin() extends StubPlugin[P2DependsOnP1](P2DependsOnP1()) with PluginWithDependencies {
    override def dependsOn: Seq[PluginDependency] = Seq(classOf[P1])
  }

}

class PluginAwareImplTest extends FreeSpec {

  import PluginAwareImplTest._

  "Plugin API" - {

    "Retrieve the version of a registered plugin" in {

      implicit val p = TestSupport.createMainProject
      val pluginProject = p.asInstanceOf[PluginAwareImpl]
      pluginProject.registerPlugin(classOf[Fake].getName, classOf[FakePlugin].getName, "1.2.3", classOf[Fake].getClassLoader)
      assert(Plugin.version[Fake] === Some("1.2.3"))

      case class Fake2()
      assert(Plugin.version[Fake2] === None)

    }

  }

  "Basic Plugin Dependencies" - {

    "Plugin P2 depends on Plugin P1" in {
      implicit val p = TestSupport.createMainProject
      val pluginProject = p.asInstanceOf[PluginAwareImpl]
      pluginProject.registerPlugin(classOf[P1].getName, classOf[P1Plugin].getName, "1.0.0", classOf[P1].getClassLoader)
      pluginProject.registerPlugin(classOf[P2DependsOnP1].getName, classOf[P2DependsOnP1Plugin].getName, "1.0.0", classOf[P2DependsOnP1].getClassLoader)

      assert(Plugin[P2DependsOnP1].get.getClass === classOf[P2DependsOnP1])
    }

    "Plugin P2 depends on unregistered Plugin P1 and will fail" in {
      implicit val p = TestSupport.createMainProject
      val pluginProject = p.asInstanceOf[PluginAwareImpl]
      //            pluginProject.registerPlugin(classOf[P1].getName, classOf[P1Plugin].getName, "1.0.0", classOf[P1].getClassLoader)
      pluginProject.registerPlugin(classOf[P2DependsOnP1].getName, classOf[P2DependsOnP1Plugin].getName, "1.0.0", classOf[P2DependsOnP1].getClassLoader)

      intercept[ProjectConfigurationException] {
        Plugin[P2DependsOnP1]
      }
    }

    "Plugin P2 depends on Plugin P1 with to low version will fail" in pending

    "Plugin P2 depends on Plugin P1 with to high version will fail" in pending

  }

  "PluginHandle API" - {

  }

}