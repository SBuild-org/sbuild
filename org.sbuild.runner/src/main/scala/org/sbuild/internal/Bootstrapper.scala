package org.sbuild.internal

import org.sbuild.Project

trait Bootstrapper {
  
  def applyToProject(project: Project) = {} 

}