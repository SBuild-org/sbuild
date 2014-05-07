package org.sbuild.internal

import scala.reflect.ClassTag
import scala.reflect.classTag
import org.sbuild.Logger
import org.sbuild.Plugin
import org.sbuild.PluginAware
import org.sbuild.PluginWithDependencies
import org.sbuild.Project
import org.sbuild.ProjectConfigurationException
import org.sbuild.PluginConfigureAware
import org.sbuild.PluginDependency
import org.sbuild.Plugin.PluginHandle
import org.sbuild.InvalidApiUsageException

trait PluginAwareImpl extends PluginAware { projectSelf: Project =>

  class RegisteredPlugin(val instanceClassName: String,
                         val factoryClassName: String,
                         val version: String,
                         val classLoader: ClassLoader) {

    private[this] val log = Logger[RegisteredPlugin]

    lazy val pluginClass: Class[_] = try {
      log.debug("About to load plugin factory class: " + instanceClassName)
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
      classLoader.loadClass(instanceClassName)
    } catch {
      case e: ClassNotFoundException =>
        val ex = new ProjectConfigurationException(s"Plugin instance class ${instanceClassName} could not be loaded.", e)
        throw ex
    }

    lazy val factory: Plugin[_] = {
      val fac = pluginClass.getConstructors().find(c => c.getParameterTypes().size == 1 && c.getParameterTypes()(0) == classOf[Project]) match {
        case Some(ctr) =>
          log.debug("Instantiating plugin factory with constructor: " + ctr)
          ctr.newInstance(projectSelf)
        case None =>
          pluginClass.getConstructors().find(c => c.getParameterTypes().size == 0) match {
            case Some(ctr) =>
              log.debug("Instantiating plugin factory with constructor: " + ctr)
              ctr.newInstance()
            case None =>
              log.debug("Could not found any supported constructors: Found these: " + pluginClass.getConstructors().mkString("\n  "))
              throw new ProjectConfigurationException("Could not found any suitable constructors in plugin class " + pluginClass.getName)
          }
      }
      fac.asInstanceOf[Plugin[_]]
    }

    case class Instance(name: String, obj: Any, modified: Boolean)

    private[this] var _instances: Seq[Instance] = Seq()
    private[this] var _applied: Boolean = false

    def isApplied: Boolean = _applied

    private def innerGet(name: String): Instance = _instances.find(_.name == name) match {
      case Some(i) =>
        log.debug("get(" + name + ") will return an already instantiated instance: " + i.obj)
        i
      case None =>
        log.debug("get(" + name + ") triggered the creation of a new instance")
        val instance = factory.create(name)
        val wrappedI = Instance(name, instance, false)
        _instances ++= Seq(wrappedI)
        log.debug("Created and return new plugin instance: " + instance)
        wrappedI
    }

    def get(name: String): Any = innerGet(name).obj
    def exists(name: String): Boolean = _instances.exists(_.name == name)
    def isModified(name: String): Boolean = innerGet(name).modified

    def update(name: String, update: Any => Any): Any = {
      val instance = get(name)
      val updatedInstance = update(instance)
      _instances = _instances.map {
        case Instance(n, i, _) if n == name => Instance(n, updatedInstance, true)
        case x => x
      }
      updatedInstance
    }

    private[this] var _postUpdates: Seq[(String, Any => Any)] = Seq()

    def postUpdate(name: String, update: Any => Any): Unit = {
      // trigger creation of instance
      val instance = get(name)
      _postUpdates ++= Seq((name, update))
    }

    def getInstanceNames: Seq[String] = _instances.map(_.name)
    def getAll: Seq[Any] = _instances.map(_.obj)

    def applyToProject: Unit = {
      if (_applied) throw new IllegalStateException("Plugin instance already applied")
      _applied = true
      if (!_instances.isEmpty) {
        log.debug("About to run applyToProject for plugin: " + this)
        // TODO: cover this with a unit test to detect refactorings at test time
        try {
          _postUpdates.foreach {
            case (name, update) =>
              val instance = get(name)
              val updatedInstance = update(instance)
              _instances = _instances.map {
                case Instance(n, i, _) if n == name => Instance(n, updatedInstance, true)
                case x => x
              }
          }
          factory.
            asInstanceOf[{ def applyToProject(instances: Seq[(String, Any)]) }].
            applyToProject(_instances.map(i => (i.name -> i.obj)))
        } catch {
          case e: ClassCastException =>
            val ex = new ProjectConfigurationException("Plugin configuration could to be applied to project: " + instanceClassName)
            ex.buildScript = Some(projectSelf.projectFile)
            throw ex
        }
      }
    }

    def dependencies: Seq[PluginDependency] = factory match {
      case p: PluginWithDependencies => p.dependsOn
      case _ => Seq()
    }

    override def toString: String = getClass.getSimpleName +
      "(instanceClassName=" + instanceClassName +
      ",factoryClassName=" + factoryClassName +
      ",version=" + version +
      ",classLoader=" + classLoader +
      ",instances=" + _instances + ")"

    def toCompactString = s"${instanceClassName}=${factoryClassName};version=${version}"

    lazy val osgiVersion = OSGiVersion.parseVersion(version)

  }

  private[this] val log = Logger[PluginAwareImpl]

  // we assume, plugins are registered in that order so that dependencies are already registered before
  private[this] var _plugins: Seq[RegisteredPlugin] = Seq()

  def registerPlugin(instanceClassName: String, factoryClassName: String, version: String, classLoader: ClassLoader) = {
    val reg = new RegisteredPlugin(instanceClassName, factoryClassName, version, classLoader)
    log.debug("About to register plugin: " + reg)
    _plugins.find(p => p.instanceClassName == instanceClassName).map { p =>
      log.warn {
        val clInfo = if (p.classLoader == classLoader) "" else "from another classloader"
        s"Already another registration for that plugin class name ${clInfo} detected."
      }
    }
    _plugins ++= Seq(reg)
  }

  private[this] var assertPluginCache: Set[RegisteredPlugin] = Set()

  def assertPlugin[T: ClassTag]: RegisteredPlugin = {
    val pluginClass = classTag[T].runtimeClass
    _plugins.find(rp => rp.instanceClass == pluginClass) match {
      case Some(rp) if assertPluginCache.contains(rp) => rp

      case Some(rp) =>
        log.debug(s"About to check plugin dependencies: ${rp.toCompactString}")

        val foundDeps = rp.dependencies.map {
          case dep @ PluginDependency.Basic(pluginClass) =>
            dep -> _plugins.find(rp => rp.instanceClass == pluginClass)
          case dep @ PluginDependency.Versioned(pluginClass, version) =>
            val range = OSGiVersionRange.parseVersionOrRange(version)
            dep -> _plugins.find(rp => rp.instanceClass == pluginClass && range.includes(rp.osgiVersion))
        }
        val (missingDeps, resolvedDeps) = foundDeps.partition { case (_, rp) => rp.isEmpty }
        if (!missingDeps.isEmpty) {
          val formattedErrors = missingDeps.mkString("\n - ", "\n - ", "")

          val ex = new ProjectConfigurationException(s"Unresolved plugin dependencies of plugin ${rp.toCompactString}.\nUnresolved dependencies:${formattedErrors}")
          ex.buildScript = Some(projectFile)
          throw ex
        } else {
          log.debug(s"All plugin dependencies of plugin ${rp.toCompactString} resolved: ${resolvedDeps.map(_._1)}")
        }

        assertPluginCache += rp
        rp

      case None =>
        val ex = new ProjectConfigurationException("No plugin registered with instance type: " + classTag[T].runtimeClass.getName)
        ex.buildScript = Some(projectSelf.projectFile)
        throw ex
    }
  }

  private def withPlugin[T: ClassTag, R](action: RegisteredPlugin => R): R = {
    log.debug("About to access plugin " + classTag[T].runtimeClass.getName)
    val regPlugin = assertPlugin[T]
    action(regPlugin)
  }

  override def finalizePlugins: Unit = {

    log.debug("About to finalize all activated plugins: " + _plugins.map(_.toCompactString))

    val orderedClasses = new DependentClassesOrderer().orderClasses(
      classes = _plugins.map(rp => rp.instanceClass),
      dependencies = _plugins.flatMap(p => p.dependencies.map(d => d.pluginClass -> p.instanceClass))
    )

    val orderedPlugins = orderedClasses.map { instanceClass =>
      _plugins.find(rp => rp.instanceClass == instanceClass) match {
        case None => throw new IllegalStateException(s"Could not found registered plugin with instance class type ${instanceClass}")
        case Some(c) => c
      }
    }

    log.info("Re-ordered plugins: " + orderedPlugins.map(_.toCompactString).zipWithIndex)

    orderedPlugins.map { rp => rp.applyToProject }
  }

  override def getPluginVersion[T: ClassTag]: Option[String] = {
    val pluginClass = classTag[T].runtimeClass
    _plugins.find(p => p.instanceClass == pluginClass).map(rp => rp.version)

  }

  override def isPluginModified[T: ClassTag](name: String): Boolean =
    withPlugin[T, Boolean] { rp => rp.isModified(name) }

  def isPluginActive[T: ClassTag](name: String): Boolean =
    withPlugin[T, Boolean] { rp => rp.exists(name) }

  private[this] var pluginHandles: Map[(Class[_], String), PluginHandle[_]] = Map()

  override def getPluginHandle[T: ClassTag](name: String): Plugin.PluginHandle[T] = withPlugin[T, Plugin.PluginHandle[T]] { rp =>
    if(name.size == 0) {
      throw new InvalidApiUsageException("A plugin configuration must have a non-empty name.")
    }
    
    val pluginClass = classTag[T].runtimeClass
    pluginHandles.get((pluginClass, name)) match {
      case Some(handle) => handle.asInstanceOf[Plugin.PluginHandle[T]]
      case None =>

        def runConfiguredHook[T](name: String, instance: T, initial: Boolean = false): Unit = {
          rp.factory match {
            case configAware: PluginConfigureAware[T] =>
              log.debug(s"Trigger ${if (initial) "initial " else ""} configured hook of instance '${name}' of plugin ${rp.toCompactString}")
              configAware.configured(name, instance)
            case _ =>
          }
        }

        // init this instance
        // TODO: later, we will require, that it is not init before
        if (!rp.exists(name)) {
          // trigger instance creation
          log.debug(s"Activating instance '${name}' of plugin ${rp.toCompactString}")
          val instance = rp.get(name)
          runConfiguredHook(name, instance, initial = true)
        } else {
          log.warn(s"Expected, that plugin instance with name '${name}' was not initializized before, but it was: ${rp.toCompactString}")
        }

        // now, we can assume an always initialized plugin instance
        val handle = new Plugin.PluginHandle[T] {
          override def get: T = rp.get(name).asInstanceOf[T]

          override def isModified: Boolean = rp.isModified(name)

          override def configure(configurer: T => T): Plugin.PluginHandle[T] = {
            log.debug(s"Configuring instance '${name}' of plugin ${rp.toCompactString}")
            rp.update(name, { instance =>
              val updatedInstance = configurer(instance.asInstanceOf[T])
              log.debug(s"Configuration of instance '${name}': ${updatedInstance}")
              runConfiguredHook[T](name, updatedInstance)
              updatedInstance
            })
            this
          }

          override def postConfigure(configurer: T => T): Plugin.PluginHandle[T] = {
            log.debug(s"Post-configuring instance '${name}' of plugin ${rp.toCompactString}")
            rp.postUpdate(name, { instance =>
              val updatedInstance = configurer(instance.asInstanceOf[T])
              log.debug(s"Configuration of instance '${name}': ${updatedInstance}")
              runConfiguredHook[T](name, updatedInstance)
              updatedInstance
            })
            this
          }
        }

        pluginHandles += (pluginClass, name) -> handle

        handle
    }
  }

  case class PluginInfo(override val name: String,
                        override val version: String,
                        override val instances: Seq[String])
      extends Plugin.PluginInfo

  override def registeredPlugins: Seq[PluginInfo] = {
    _plugins.map { p =>
      PluginInfo(name = p.instanceClassName, p.version, instances = p.getInstanceNames)
    }
  }

}
