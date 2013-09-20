package de.tototec.sbuild.runner

import java.io.File
import java.io.FileWriter
import java.util.Timer
import java.util.TimerTask

// File Locking Mechanism
// 
// Problem: 
// Independent processed try to create/modify/delete the same file. 
// There is no simple way to detect problems.
// 
// Solution Idea:
// Use an additional lock file.
// 
// Problem with a lock file:
// If the process that created the lockfile dies before deleting the lockfile, 
// all other waiting procress will wait forever.
//
// Solution:
// A lock file needs some informations:
// - touch time
// - process id
// If a process finds a lockfile, it checks the age of that lockfile by checking the touch time.
// If that file is to old, it can be assumed that the creating process dies. 
// The process that created the lockfile is responsible to refresh the touch time of the lockfile in a multithreaded way, so that it is guaranteed, the touch time is accurate and up-to-date.
// 
//
// Details: 
// Because of different file system specific time precision, to time interval has to be at least 2 seconds or must be store inside the file.
// If a lockfile of a died process was detected, the wait time should be double the time of the refresh time, e.g. 3-4 seconds.

class FileLock(file: File,
               updateIntervalMsec: Long,
               processInformation: String,
               createDirs: Boolean) {

  val timer = new Timer("Lock-" + file.getName, true /* isDaemon */ )
  def updateLock = if (file.exists) file.setLastModified(System.currentTimeMillis)

  // CREATE THE LOCK

  // init
  {
    require(!file.exists, "The lock file already exists")

    if (createDirs) {
      file.getParentFile() match {
        case null =>
        case parent => parent.mkdirs()
      }
    }

    // create the file
    val fw = new FileWriter(file)
    fw.write(processInformation)
    fw.close()

    // create and schedule a timer to update the touch time
    val timerTask = new TimerTask() {
      override def run() = updateLock
    }
    timer.scheduleAtFixedRate(timerTask, 0 /* delay */ , updateIntervalMsec /* interval */ )
  }

  // RELEASE THE LOCK

  private[this] var released: Boolean = false

  // Release this lock
  def release = if (!released) {
    timer.cancel
    val success = file.delete
    released = true
    if (!success) throw new IllegalStateException("Could not delete the lock file: " + file)
  }

}
