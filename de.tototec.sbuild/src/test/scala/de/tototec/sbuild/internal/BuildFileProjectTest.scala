package de.tototec.sbuild.internal

import org.scalatest.FunSuite

import de.tototec.sbuild.Plugin
import de.tototec.sbuild.Project
import de.tototec.sbuild.test.TestSupport

object BuildFileProjectTest {

  class TestPluginCtx(name: String)

  class TestPlugin(implicit project: Project) extends Plugin[TestPluginCtx] {
    override def instanceType: Class[TestPluginCtx] = classOf[TestPluginCtx]
    //      override def defaultName: String = "testPlugin"
    override def create(name: String): TestPluginCtx = new TestPluginCtx(name)
    override def applyToProject(instances: Seq[(String, TestPluginCtx)]) {}
  }

}

class BuildFileProjectTest extends FunSuite {
  import BuildFileProjectTest._

  test("Find an added pluginFactory") {

    implicit val p = TestSupport.createMainProject

    //    val plugin = new TestPlugin
    p.registerPlugin(classOf[TestPluginCtx].getName, classOf[TestPlugin].getName, classOf[TestPlugin].getClassLoader)

    val foundInstance = p.findOrCreatePluginInstance[TestPluginCtx]("")
    assert(foundInstance !== null)

    val foundDefaultInstance = p.findOrCreatePluginInstance[TestPluginCtx]("")
    assert(foundInstance === foundDefaultInstance)

  }

}