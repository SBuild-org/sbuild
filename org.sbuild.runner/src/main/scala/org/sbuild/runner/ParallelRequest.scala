package org.sbuild.runner

import org.sbuild.Project
import org.sbuild.Target
import org.sbuild.TargetRefs
import org.sbuild.internal.ProjectTarget

object ParallelRequest {

  def apply(request: TargetRefs)(implicit project: Project): Target =
    new ProjectTarget(name = "phony:@parallel-request",
      file = null,
      phony = true,
      project = project,
      initialPrereqs = request,
      initialTransparentExec = true,
      initialSideEffectFree = true
    )

}