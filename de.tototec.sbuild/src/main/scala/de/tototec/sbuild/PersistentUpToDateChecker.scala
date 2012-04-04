package de.tototec.sbuild

import java.io.{ File => JFile }
import scala.tools.nsc.io.Directory
import scala.tools.nsc.io.File
import java.util.Properties
import scala.collection.JavaConversions._

object PersistentUpToDateChecker {

  def apply(uniqueId: String, srcDir: JFile, stateDir: JFile) =
    new PersistentUpToDateChecker(uniqueId, srcDir, stateDir)

  def apply(uniqueId: String, srcDir: JFile, stateDir: JFile, dependencies: TargetRef*)(implicit project: Project) = {
    val checker = new PersistentUpToDateChecker(uniqueId, srcDir, stateDir)
    checker.addDependencies(dependencies: Seq[TargetRef])
    checker
  }

  def apply(uniqueId: String, srcDir: JFile, stateDir: JFile, dependencies: TargetRefs)(implicit project: Project) = {
    val checker = new PersistentUpToDateChecker(uniqueId, srcDir, stateDir)
    checker.addDependencies(dependencies)
    checker
  }

}

class PersistentUpToDateChecker(checkerUniqueId: String, srcDir: JFile, stateDir: JFile) {

  def stateFile: File = Directory(stateDir) / File(".filestates." + checkerUniqueId)

  private var additionalFiles: Seq[File] = Seq()
  private var additionalPhony: Seq[String] = Seq()

  def addPhonyDependencies(phonys: String*) = additionalPhony ++= phonys

  def addDependencies(files: JFile*) = additionalFiles ++= files.map(File(_))

  def addDependencies(targetRefs: TargetRefs)(implicit project: Project): Unit = addDependencies(targetRefs.targetRefs: _*)

  def addDependencies(targetRefs: TargetRef*)(implicit project: Project): Unit = targetRefs.foreach { tr =>
    project.findTarget(tr) match {
      case None => throw new IllegalArgumentException("Cannot found target: " + tr)
      case Some(target) =>
        target.phony match {
          case true => addPhonyDependencies(target.file.getPath)
          case false => addDependencies(target.file)
        }
    }
  }

  def createStateMap: Map[String, Long] = (
    ((Directory(srcDir).deepFiles ++ additionalFiles).map { foundFile =>
      (foundFile.path -> (if (foundFile.exists) foundFile.lastModified else (0: Long)))
    }) ++
    (additionalPhony.map { phony => (phony -> (0: Long)) })
  ).toMap

  def readPersistentMap: Option[Map[String, Long]] = {
    if (!stateFile.exists) {
      None
    } else {
      val props = new Properties()
      props.load(stateFile.inputStream)
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
    stateFile.parent.createDirectory(true, false)
    props.store(stateFile.outputStream(false), getClass.getName + " id:" + checkerUniqueId)
  }

  def cleanup {
    stateFile.deleteIfExists
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
  def doWhenNotUpToDate(action: => Unit): Boolean = {
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