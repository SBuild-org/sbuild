package de.tototec.sbuild.internal

import org.scalatest.FunSuite

import de.tototec.sbuild.Plugin
import de.tototec.sbuild.Project
import de.tototec.sbuild.test.TestSupport

class BuildFileProjectTest extends FunSuite {

  test("Find an added pluginFactory") {

    class TestPluginCtx(name: String)

    class TestPlugin(implicit project: Project) extends Plugin[TestPluginCtx] {
      override def instanceType: Class[TestPluginCtx] = classOf[TestPluginCtx]  
      override def defaultName: String = "testPlugin"
      override def create(name: String): TestPluginCtx = new TestPluginCtx(name)
      override def applyToProject(name: String, pluginContext: TestPluginCtx) {}
    }

    implicit val p = TestSupport.createMainProject

    val plugin = new TestPlugin
    p.registerPlugin(plugin, Plugin.Config())
    
    val foundInstance = p.findOrCreatePluginInstance[TestPluginCtx, TestPlugin](plugin.defaultName)
    assert(foundInstance !== null)

    val foundDefaultInstance = p.findOrCreatePluginInstance[TestPluginCtx, TestPlugin]
    assert(foundInstance === foundDefaultInstance)

  }

}