package de.tototec.sbuild

import java.io.File
import java.util.Properties
import scala.collection.JavaConversions._
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Execute a given task only if a given set of sources have changed.
 * 
 * A (hidden) state file will be written into the directory given with <code>stateDir</code>.
 * If a previously created state file does not exists, the task will always be executed.
 * Therefore it is suggested, to place the state file into a directory, which gets clean automaticaly in a "clean" tasks.
 * The fact, if the task was executed or not, is reported back to the given TargetContext, so that tasks,
 * that depends on this tasks might be skipped, if this tasks was already up-to-date.
 *
 */
object IfNotUpToDate {

  def apply(srcDir: File, stateDir: File, ctx: TargetContext)(task: => Any)(implicit project: Project): Unit =
    apply(Seq(srcDir), stateDir, ctx)(task)

  def apply(srcDirOrFiles: Seq[File], stateDir: File, ctx: TargetContext)(task: => Any)(implicit project: Project) {
    val checker = PersistentUpToDateChecker(ctx.name.replaceFirst("^phony:", ""), srcDirOrFiles, stateDir, ctx.prerequisites)
    val didSomething = checker.doWhenNotUpToDate(task)
    if (didSomething) project.log.log(LogLevel.Debug, "Conditional action executed in target " + ctx.name)
    else project.log.log(LogLevel.Debug, "Conditional action not executed because it was up-to-date in target " + ctx.name)
    ctx.addToTargetWasUpToDate(!didSomething)
  }

}

object PersistentUpToDateChecker {

  def apply(uniqueId: String, srcDir: File, stateDir: File) =
    new PersistentUpToDateChecker(uniqueId, Seq(srcDir), stateDir)
  def apply(uniqueId: String, srcDirOrFiles: Seq[File], stateDir: File) =
    new PersistentUpToDateChecker(uniqueId, srcDirOrFiles, stateDir)

  def apply(uniqueId: String,
            srcDir: File,
            stateDir: File,
            dependencies: TargetRef*)(implicit project: Project): PersistentUpToDateChecker =
    apply(uniqueId, Seq(srcDir), stateDir, dependencies)
  def apply(uniqueId: String,
            srcDirOrFiles: Seq[File],
            stateDir: File,
            dependencies: TargetRef*)(implicit project: Project): PersistentUpToDateChecker = {
    val checker = new PersistentUpToDateChecker(uniqueId, srcDirOrFiles, stateDir)
    checker.addDependencies(dependencies: Seq[TargetRef])
    checker
  }

  def apply(uniqueId: String,
            srcDir: File,
            stateDir: File,
            dependencies: TargetRefs)(implicit project: Project): PersistentUpToDateChecker =
    apply(uniqueId, Seq(srcDir), stateDir, dependencies)
  def apply(uniqueId: String,
            srcDirOrFiles: Seq[File],
            stateDir: File,
            dependencies: TargetRefs)(implicit project: Project): PersistentUpToDateChecker = {
    val checker = new PersistentUpToDateChecker(uniqueId, srcDirOrFiles, stateDir)
    checker.addDependencies(dependencies)
    checker
  }

}

class PersistentUpToDateChecker(checkerUniqueId: String, srcDirOrFiles: Seq[File], stateDir: File) {

  def stateFile: File = new File(stateDir, ".filestates." + checkerUniqueId)

  private var additionalFiles: Seq[File] = Seq()
  private var additionalPhony: Seq[String] = Seq()

  def addPhonyDependencies(phonys: String*) = additionalPhony ++= phonys

  def addDependencies(files: File*) = additionalFiles ++= files

  def addDependencies(targetRefs: TargetRefs)(implicit project: Project): Unit = addDependencies(targetRefs.targetRefs: _*)

  def addDependencies(targetRefs: TargetRef*)(implicit project: Project): Unit = targetRefs.foreach { tr =>
    project.findTarget(tr, searchInAllProjects = true) match {
      case None => throw new IllegalArgumentException("Could not found target: " + tr)
      case Some(target) =>
        target.phony match {
          case true => addPhonyDependencies(target.file.getPath)
          case false => addDependencies(target.file)
        }
    }
  }

  def createStateMap: Map[String, Long] = (
    ((srcDirOrFiles.flatMap { f =>
      if (f.isDirectory) Util.recursiveListFiles(f, ".*".r) else Seq(f)
    } ++ additionalFiles).map { foundFile =>
      (foundFile.getPath -> (if (foundFile.exists) foundFile.lastModified else (0: Long)))
    }
    ) ++
    (additionalPhony.map { phony => (phony -> (0: Long)) })
  ).toMap

  def readPersistentMap: Option[Map[String, Long]] = {
    if (!stateFile.exists) {
      None
    } else {
      val props = new Properties()
      props.load(new FileInputStream(stateFile))
      Some(props.iterator.map {
        case (key, value) => (key -> value.toLong)
      }.toMap)
    }
  }

  def writeStateMap(stateMap: Map[String, Long]) {
    val props = new Properties()
    stateMap.foreach {
      case (key, value) => props.setProperty(key, value.toString)
    }
    stateFile.getParentFile.mkdirs
    val outStream = new FileOutputStream(stateFile, false)
    try {
      props.store(outStream, getClass.getName + " id:" + checkerUniqueId)
    } finally {
      if (outStream != null) outStream.close()
    }
  }

  def cleanup {
    if (stateFile.exists) {
      stateFile.delete
    }
  }

  def checkUpToDate(currentStateMap: Map[String, Long]): Boolean = readPersistentMap match {
    case None =>
      // no persistent state, not up-to-date
      false
    case Some(map) =>
      // compare read file map with found files
      if (map.size != currentStateMap.size) false
      else {
        map.forall {
          case (name, time) =>
            val curTime = currentStateMap.getOrElse(name, 0)
            curTime != 0 && curTime == time
        }
      }
  }

  /**
   * Execute the action only it the persistent state indicates a changes.
   * @return <code>true</code> if the action was executed, <code>false</code> when nothing was done
   */
  def doWhenNotUpToDate(action: => Any): Boolean = {
    // evaluate files state
    val stateMap = createStateMap
    checkUpToDate(stateMap) match {
      case true => false
      case false =>
        // remove old state map
        cleanup
        // execute action
        action
        // no errors
        writeStateMap(stateMap)
        true
    }
  }

}