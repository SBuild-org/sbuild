package de.tototec.sbuild.experimental.aether

import java.io.File

import de.tototec.sbuild.MavenSupport.MavenGav

trait AetherSchemeHandlerWorker {
  def resolve(requestedArtifacts: Seq[MavenGav]): Seq[File]
}
  
