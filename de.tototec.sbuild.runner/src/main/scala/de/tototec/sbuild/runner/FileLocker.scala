package de.tototec.sbuild.runner

import java.io.File

class FileLocker(updateIntervalMsec: Long = 1000,
                 checkIntervalMsec: Long = 1000,
                 orphanLockfileTimeoutMsec: Long = 4000) {

  /**
   * Acquire the lock.
   * If the lock count not be acquired after a timeout, the method returns without acquiring a lock.
   *
   * @param file The lock file to be used.
   * @param timeoutMsec The timeout in milliseconds to try to acquire the timeout.
   * A timeout of zero (0) means try infinitely.
   * @param processInformation Some information which can be used by another process to get additional information about the lock creator.
   * @return Either a `Right[FileLock]` containing the successful acquired file lock or a `Left[String]` containing some failure information.
   */
  def acquire(file: File, timeoutMsec: Long, processInformation: String, createDirs: Boolean = true): Either[String, FileLock] = {
    val startTime = System.currentTimeMillis
    var newTimeOut = timeoutMsec
    while (newTimeOut >= 0 && file.exists) {
      val checkTime = System.currentTimeMillis
      val lastTouchTime = file.lastModified
      val fileToOldTime = checkTime - orphanLockfileTimeoutMsec
      if (lastTouchTime < fileToOldTime) {
        // About to delete orphan lock file
        // TODO: read file content, as it contains some information about the process, that just died.
        file.delete
      } else {
        newTimeOut = timeoutMsec - (checkTime - startTime)
        if (newTimeOut >= 0) {
          Thread.sleep(math.min(newTimeOut, checkIntervalMsec))
        }
      }
    }
    if (file.exists) Left(s"Lock could not acquired within an timeout of ${timeoutMsec} msec.")
    else Right(new FileLock(file, updateIntervalMsec, processInformation, createDirs))
  }

}