package de.tototec.sbuild.internal

import scala.reflect.ClassTag
import scala.reflect.classTag
import de.tototec.sbuild.Plugin
import de.tototec.sbuild.PluginAware
import de.tototec.sbuild.Project
import de.tototec.sbuild.ProjectConfigurationException
import de.tototec.sbuild.Logger

trait PluginAwareImpl extends PluginAware { projectSelf: Project =>

  class RegisteredPlugin(val plugin: Plugin[_], config: Plugin.Config) { // (implicit project: Project) {

    private[this] val log = Logger[RegisteredPlugin]

    def pluginType: Class[_] = plugin.getClass
    def instanceType: Class[_] = plugin.instanceType

    private[this] var _instances: Seq[(String, Any)] = Seq()

    def get(name: String): Any = _instances.find(_._1 == name) match {
      case Some((_, instance)) =>
        log.debug("get(" + name + ") will return an already instantiated instance: " + instance)
        instance
      case None =>
        if (name != plugin.defaultName && config.singleton) {
          throw new ProjectConfigurationException("Plugin \"" + pluginType + "\" is a singleton. Creating a second instance with name \"" + name + "\" is not allowed.")
        }
        log.debug("get(" + name + ") triggered the creation of a new instance")
        val instance = plugin.create(name)
        _instances ++= Seq(name -> instance)
        log.debug("Created and return new instance: " + instance)
        instance
    }

    def getAll: Seq[Any] = _instances.map(_._2)

    def applyToProject: Unit = {
      _instances.map {
        case (name, instance) =>
          try {
            //  pluginType.getMethod("applyToProject", instanceType).invoke(plugin, instance.asInstanceOf[Object])
            plugin.asInstanceOf[{ def applyToProject(n: String, i: Any) }].applyToProject(name, instance)
          } catch {
            case e: ClassCastException => throw new ProjectConfigurationException("Named Instance \"" + name + "\" of plugin \"" + pluginType.getName + "\" is not of type \"" + instanceType + "\".")
          }
      }
    }

    // trigger creation of the default instance
    get(plugin.defaultName).getClass

    override def toString: String = getClass.getSimpleName +
      "(pluginType=" + pluginType +
      ",instanceType=" + instanceType +
      ",instances=" + _instances + ")"
  }

  private[this] val log = Logger[PluginAwareImpl]

  // we assume, plugins are registered in that order so that dependencies are already registered before
  private[this] var _plugins: Seq[RegisteredPlugin] = Seq()

  override def registerPlugin(pluginClass: Class[_], config: Plugin.Config): Unit = {
    if (!classOf[Plugin[_]].isAssignableFrom(pluginClass)) {
      val ex = new ProjectConfigurationException("Plugin class \"" + pluginClass.getName + "\" is not an instance of Plugin.")
      ex.buildScript = Some(projectSelf.projectFile)
      throw ex
    }
    val plugin = pluginClass.getConstructors().find(c => c.getParameterTypes().size == 1 && c.getParameterTypes()(0) == classOf[Project]) match {
      case Some(ctr) =>
        log.debug("Creating a plugin instance with constructor: " + ctr)
        ctr.newInstance(projectSelf)
      case None =>
        pluginClass.getConstructors().find(c => c.getParameterTypes().size == 0) match {
          case Some(ctr) =>
            log.debug("Creating a plugin instance with constructor: " + ctr)
            ctr.newInstance()
          case None =>
            log.debug("Could not found any supported constructors: Found these: " + pluginClass.getConstructors().mkString("\n  "))
            throw new ProjectConfigurationException("Could not found any suitable constructors in plugin class " + pluginClass.getName)
        }
    }
    registerPlugin(plugin.asInstanceOf[Plugin[_]], config)
  }

  // TODO: check, that same instance type is not registered by two different plugins
  override def registerPlugin(plugin: Plugin[_], config: Plugin.Config): Unit = {
    _plugins ++= Seq(new RegisteredPlugin(plugin, config))
  }

  override def findOrCreatePluginInstance[I: ClassTag, T <: Plugin[I]: ClassTag]: I = findOrCreatePluginInstance[I, T]("")

  override def findOrCreatePluginInstance[I: ClassTag, T <: Plugin[I]: ClassTag](name: String): I = {
    log.debug("About to findOrCreatePluginInstance[" + classTag[I].runtimeClass.getName + "," + classTag[T].runtimeClass.getName + "](" + name + ")")
    _plugins.find { rp =>
      log.debug("checking " + rp)
      classTag[I].runtimeClass.isAssignableFrom(rp.instanceType) && classTag[T].runtimeClass.isAssignableFrom(rp.pluginType)
    } match {
      case Some(regPlugin) =>
        val instanceName = if (name == "") regPlugin.plugin.defaultName else name
        regPlugin.get(instanceName).asInstanceOf[I]
      case None =>
        throw new ProjectConfigurationException("No plugin registered with instance type: " + classTag[I].runtimeClass.getName)
    }
  }

  override def finalizePlugins: Unit = _plugins.map(_.applyToProject)

}