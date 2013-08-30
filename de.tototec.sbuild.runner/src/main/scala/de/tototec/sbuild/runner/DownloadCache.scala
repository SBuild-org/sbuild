package de.tototec.sbuild.runner

import java.net.URL
import java.io.File

trait DownloadCache {
  def hasEntry(url: URL): Boolean
  def registerEntry(url: URL, file: File)
  def getEntry(url: URL): File
}

class SimpleDownloadCache extends DownloadCache {
  private var cache: Map[URL, File] = Map()

  override def hasEntry(url: URL): Boolean = cache.contains(url)
  override def registerEntry(url: URL, file: File) = cache += (url -> file)
  override def getEntry(url: URL): File = cache(url)

}