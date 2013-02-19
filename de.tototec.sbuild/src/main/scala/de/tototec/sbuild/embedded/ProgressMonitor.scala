package de.tototec.sbuild.embedded

trait ProgressMonitor {
  def beginTask(totalWork: Int, name: String)
  def cancelled: Boolean
  def cancel
  def done
  def worked(work: Int, name: String)
}

class NullProgressMonitor extends ProgressMonitor {
  private var _cancelled: Boolean = _
  private var _started: Boolean = _
  private var _done: Boolean = _
  private var _totalWork: Int = _
  private var _worked: Int = _

  override def beginTask(totalWork: Int, name: String) {
    _started = true
    _totalWork = totalWork
    _worked = 0
  }
  override def done {
    _started = true
    _done = true
    _worked = _totalWork

  }
  override def cancelled: Boolean = _cancelled
  override def cancel {
    if (!_done) _cancelled = true
  }
  override def worked(work: Int, name: String) = {
    if (work >= 0) _worked += work
  }
}
