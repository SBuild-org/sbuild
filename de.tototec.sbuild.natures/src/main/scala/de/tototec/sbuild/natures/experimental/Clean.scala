package de.tototec.sbuild.natures.experimental

import de.tototec.sbuild.Path
import de.tototec.sbuild.Target
import de.tototec.sbuild.TargetRefs
import de.tototec.sbuild.ant.tasks.AntDelete

trait CleanNature extends Nature { this: OutputDirConfig with ProjectConfig =>

  def clean_targetName: String = "clean"
  def clean_dependsOn: TargetRefs = TargetRefs()

  abstract override def createTargets: Seq[Target] = super.createTargets ++
    Seq(Target("phony:" + clean_targetName) dependsOn clean_dependsOn exec {
      AntDelete(dir = Path(outputDir))
    })

}
