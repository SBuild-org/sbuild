package de.tototec.sbuild.ant.tasks

import java.io.File

import org.apache.tools.ant.taskdefs.Expand

import de.tototec.sbuild.ant.AntProject
import de.tototec.sbuild.Project

object AntExpand {
  def apply(src: File = null,
            dest: File = null,
            overwrite: java.lang.Boolean = null,
            encoding: String = null,
            stripAbsolutePathSpec: java.lang.Boolean = null,
            scanForUnicodeExtraFields: java.lang.Boolean = null)(implicit _project: Project) =
    new AntExpand(
      src = src,
      dest = dest,
      overwrite = overwrite,
      encoding = encoding,
      stripAbsolutePathSpec = stripAbsolutePathSpec,
      scanForUnicodeExtraFields = scanForUnicodeExtraFields
    ).execute
}

class AntExpand()(implicit _project: Project) extends Expand {
  setProject(AntProject())

  def this(src: File = null,
           dest: File = null,
           overwrite: java.lang.Boolean = null,
           encoding: String = null,
           stripAbsolutePathSpec: java.lang.Boolean = null,
           scanForUnicodeExtraFields: java.lang.Boolean = null)(implicit _project: Project) {
    this
    if (src != null) setSrc(src)
    if (dest != null) setDest(dest)
    if (overwrite != null) setOverwrite(overwrite.booleanValue)
    if (encoding != null) setEncoding(encoding)
    if (stripAbsolutePathSpec != null) setStripAbsolutePathSpec(stripAbsolutePathSpec.booleanValue)
    if (scanForUnicodeExtraFields != null) setScanForUnicodeExtraFields(scanForUnicodeExtraFields.booleanValue)
  }

}