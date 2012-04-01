package de.tototec.sbuild

import java.io.File
import java.util.Date

class ExecContext(target: Target, val startTime: Date = new Date()) {
  // prerequisites (target or ctx)
  // allPrerequisites
  // target name
  // target file
  def targetFile: Option[File] = target.targetFile
  var endTime: Date = _
  def execDurationMSec: Long = (endTime match {
    case null => new Date()
    case x => x
  }).getTime - startTime.getTime
  def prerequisites: Seq[TargetRef] = target.dependants
}