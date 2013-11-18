package de.tototec.sbuild.internal

import scala.reflect.ClassTag
import scala.reflect.classTag
import de.tototec.sbuild.Plugin
import de.tototec.sbuild.PluginAware
import de.tototec.sbuild.Project
import de.tototec.sbuild.ProjectConfigurationException
import de.tototec.sbuild.Logger

trait PluginAwareImpl extends PluginAware { projectSelf: Project =>

  class RegisteredPlugin(val instanceClassName: String, val factoryClassName: String, val classLoader: ClassLoader) {

    private[this] val log = Logger[RegisteredPlugin]

    lazy val pluginClass: Class[_] = try {
      log.debug("About to load plugin factroy class: " + instanceClassName)
      val t = classLoader.loadClass(factoryClassName)
      if (!classOf[Plugin[_]].isAssignableFrom(t)) {
        // TODO specific exception
        val ex = new ProjectConfigurationException(s"Plugin factory class ${factoryClassName} does not implement ${classOf[Plugin[_]].getName} trait.")
        ex.buildScript = Some(projectSelf.projectFile)
        throw ex
      }
      t
    } catch {
      case e: ClassNotFoundException =>
        val ex = new ProjectConfigurationException(s"Plugin factory class ${factoryClassName} could not be loaded.", e)
        ex.buildScript = Some(projectSelf.projectFile)
        throw ex
    }

    lazy val instanceClass: Class[_] = try {
      log.debug("About to load plugin instance class: " + instanceClassName)
      val t = classLoader.loadClass(instanceClassName)
      if (!classOf[Plugin[_]].isAssignableFrom(t)) {
        // TODO specific exception
        val ex = new ProjectConfigurationException(s"Plugin instance class ${factoryClassName} does not implement ${classOf[Plugin[_]].getName} trait.")
        ex.buildScript = Some(projectSelf.projectFile)
        throw ex
      }
      t
    } catch {
      case e: ClassNotFoundException =>
        val ex = new ProjectConfigurationException(s"Plugin instance class ${factoryClassName} could not be loaded.", e)
        throw ex
    }

    lazy val factory: Plugin[_] = {
      val fac = pluginClass.getConstructors().find(c => c.getParameterTypes().size == 1 && c.getParameterTypes()(0) == classOf[Project]) match {
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
      fac.asInstanceOf[Plugin[_]]
    }

    private[this] var _instances: Seq[(String, Any)] = Seq()

    def get(name: String): Any = _instances.find(_._1 == name) match {
      case Some((_, instance)) =>
        log.debug("get(" + name + ") will return an already instantiated instance: " + instance)
        instance
      case None =>
        log.debug("get(" + name + ") triggered the creation of a new instance")
        val instance = factory.create(name)
        _instances ++= Seq(name -> instance)
        log.debug("Created and return new plugin instance: " + instance)
        instance
    }

    def getAll: Seq[Any] = _instances.map(_._2)

    def applyToProject: Unit = {
      if (!_instances.isEmpty) {
        log.debug("About to run applyToProject for plugin: " + this)
        // TODO: cover this with a unit test to detect refactorings at test time
        try {
          factory.
            asInstanceOf[{ def applyToProject(instances: Seq[(String, Any)]) }].
            applyToProject(_instances)
        } catch {
          case e: ClassCastException =>
            val ex = new ProjectConfigurationException("Plugin configuration could to be applied to project: " + instanceClassName)
            ex.buildScript = Some(projectSelf.projectFile)
            throw ex
        }
      }
    }

    override def toString: String = getClass.getSimpleName +
      "(instanceClassName=" + instanceClassName +
      ",factoryClassName=" + factoryClassName +
      ",classLoader=" + classLoader +
      ",instances=" + _instances + ")"
  }

  private[this] val log = Logger[PluginAwareImpl]

  // we assume, plugins are registered in that order so that dependencies are already registered before
  private[this] var _plugins: Seq[RegisteredPlugin] = Seq()

  def registerPlugin(instanceClassName: String, factoryClassName: String, classLoader: ClassLoader) = {
    val reg = new RegisteredPlugin(instanceClassName, factoryClassName, classLoader)
    log.debug("About to register plugin: " + reg)
    _plugins ++= Seq(reg)
  }

  //  // TODO: check, that same instance type is not registered by two different plugins
  //  override def registerPlugin(plugin: Plugin[_]): Unit = {
  //    _plugins ++= Seq(new RegisteredPlugin(plugin, config))
  //  }

  //  override def findOrCreatePluginInstance[I: ClassTag, T <: Plugin[I]: ClassTag]: I = findOrCreatePluginInstance[I, T]("")

  override def findOrCreatePluginInstance[T: ClassTag](name: String): T = {
    log.debug("About to findOrCreatePluginInstance[" + classTag[T].runtimeClass.getName + "](" + name + ")")
    _plugins.find { rp =>
      log.debug("checking " + rp)
      val searchedClass = classTag[T].runtimeClass
      rp.instanceClassName == searchedClass.getName && rp.classLoader == searchedClass.getClassLoader
    } match {
      case Some(regPlugin) =>
        val instanceName = name
        regPlugin.get(instanceName).asInstanceOf[T]
      case None =>
        val ex = new ProjectConfigurationException("No plugin registered with instance type: " + classTag[T].runtimeClass.getName)
        ex.buildScript = Some(projectSelf.projectFile)
        throw ex
    }
  }

  override def finalizePlugins: Unit = _plugins.map(_.applyToProject)

}