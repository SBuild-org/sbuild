package de.tototec.sbuild.internal

import org.scalatest.FunSuite

import de.tototec.sbuild.Plugin
import de.tototec.sbuild.Project
import de.tototec.sbuild.test.TestSupport

object BuildFileProjectTest {

  case class TestPluginCtx(prop1: String)

  class TestPlugin(implicit project: Project) extends Plugin[TestPluginCtx] {
    //    override def instanceType: Class[TestPluginCtx] = classOf[TestPluginCtx]
    //      override def defaultName: String = "testPlugin"
    override def create(name: String): TestPluginCtx = TestPluginCtx(name)
    override def applyToProject(instances: Seq[(String, TestPluginCtx)]) {}
  }

}

class BuildFileProjectTest extends FunSuite {
  import BuildFileProjectTest._

  def createProjectWithPlugin: Project = {
    val p = TestSupport.createMainProject
    p.registerPlugin(classOf[TestPluginCtx].getName, classOf[TestPlugin].getName, "0.0.0", classOf[TestPlugin].getClassLoader)
    p
  }

  test("Find an added pluginFactory (inner API)") {
    implicit val p = createProjectWithPlugin

    val foundInstance = p.getPluginHandle[TestPluginCtx]("").get
    assert(foundInstance !== null)

    val foundDefaultInstance = p.getPluginHandle[TestPluginCtx]("").get
    assert(foundInstance === foundDefaultInstance)

  }

  test("Enable an unnamed plugin") {
    implicit val p = createProjectWithPlugin

    Plugin[TestPluginCtx]
  }

  test("Enable a named plugin") {
    implicit val p = createProjectWithPlugin

    Plugin[TestPluginCtx]("p-name")
  }

  test("Configure an unnamed plugin") {
    implicit val p = createProjectWithPlugin

    Plugin[TestPluginCtx] configure { _.copy(prop1 = "blau") }
  }

  test("Configure a named plugin") {
    implicit val p = createProjectWithPlugin

    Plugin[TestPluginCtx]("blau") configure { _.copy(prop1 = "blau") }
  }

  test("Plugin config should have changed after re-configuration") {
    implicit val p = createProjectWithPlugin

    val config1 = Plugin[TestPluginCtx]("blau").get
    val config1a = Plugin[TestPluginCtx]("blau").get
    assert(config1 === config1a)
    assert(config1.prop1 === "blau")
    assert(config1.prop1 === config1a.prop1)

    Plugin[TestPluginCtx]("blau") configure { c => c.copy(prop1 = "gray") }
    val config2 = Plugin[TestPluginCtx]("blau").get
    assert(config1.prop1 === "blau")
    assert(config2.prop1 === "gray")
    assert(config1 !== config2)

  }

  test("Plugin config should be equal when configuration didn't changed") {
    implicit val p = createProjectWithPlugin

    val config1 = Plugin[TestPluginCtx]("blau").get
    Plugin[TestPluginCtx]("blau") configure { _.copy(prop1 = "blau") }
    val config2 = Plugin[TestPluginCtx]("blau").get
    assert(config1.prop1 === config2.prop1)
    assert(config1 === config2)
  }
}