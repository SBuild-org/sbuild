package de.tototec.sbuild.eclipse.plugin

import org.eclipse.core.runtime.IPath
import org.eclipse.jdt.core.IJavaProject
import org.eclipse.jdt.core.IClasspathContainer
import org.eclipse.jdt.core.ClasspathContainerInitializer
import org.eclipse.jdt.core.JavaCore
import org.eclipse.core.runtime.NullProgressMonitor

class SBuildClasspathContainerInitializer extends ClasspathContainerInitializer {

  override def initialize(containerPath: IPath, project: IJavaProject): Unit = {
    debug("intialize(containerPath=" + containerPath + ", project=" + project + ")")
    setClasspathContainer(containerPath, project)
  }

  override def canUpdateClasspathContainer(containerPath: IPath, project: IJavaProject): Boolean = true

  override def requestClasspathContainerUpdate(containerPath: IPath, project: IJavaProject,
                                               containerSuggestion: IClasspathContainer) {
    debug("requestClasspathContainerUpdate(containerPath=" + containerPath + ", project=" + project + ")")
    setClasspathContainer(containerPath, project)
  }

  def setClasspathContainer(containerPath: IPath, project: IJavaProject) {
      val container = new SBuildClasspathContainer(containerPath, project)
      JavaCore.setClasspathContainer(containerPath, Array(project), Array(container), new NullProgressMonitor())
  }

}