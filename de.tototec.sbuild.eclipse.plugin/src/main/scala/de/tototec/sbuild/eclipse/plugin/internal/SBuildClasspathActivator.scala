package de.tototec.sbuild.eclipse.plugin.internal

import org.osgi.framework.BundleActivator
import org.osgi.framework.BundleContext

object SBuildClasspathActivator {
  private var _activator: SBuildClasspathActivator = _
  def activator = _activator
  def activator_=(activator: SBuildClasspathActivator) = _activator = activator
}

class SBuildClasspathActivator extends BundleActivator {

  private var _bundleContext: BundleContext = _
  def bundleContext = _bundleContext

  override def start(bundleContext: BundleContext) {
    this._bundleContext = bundleContext
    SBuildClasspathActivator.activator = this;
    Console.err.println("Started bundle: " + bundleContext.getBundle)
  }

  override def stop(bundleContext: BundleContext) {
    SBuildClasspathActivator.activator = null;
    this._bundleContext = null
  }
}
