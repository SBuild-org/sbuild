package de.tototec.sbuild.addons.bnd

import java.io.File
import java.net.URLClassLoader
import de.tototec.sbuild.Project
import de.tototec.sbuild.LogLevel

object BndJar {

  def apply(bndClasspath: Seq[File] = null,
            classpath: Seq[File] = null,
            props: Map[String, String] = null,
            destFile: File = null)(implicit project: Project) =
    new BndJar(
      bndClasspath = bndClasspath,
      classpath = classpath,
      props = props,
      destFile = destFile)

}

class BndJar(
  var bndClasspath: Seq[File] = null,
  var classpath: Seq[File] = null,
  var props: Map[String, String] = null,
  var destFile: File = null)(implicit project: Project) {

  def execute {

    val bndClassLoader = bndClasspath match {
      case null => classOf[BndJar].getClassLoader
      case cp =>
        val cl = new URLClassLoader(cp.map { f => f.toURI().toURL() }.toArray, classOf[BndJar].getClassLoader)
        project.log.log(LogLevel.Debug, "Using addional bnd classpath: " + cl.getURLs().mkString(", "))
        cl
    }

    val bndJarImplClassName = (classOf[BndJar].getPackage match {
      case null => ""
      case p => p.getName + "."
    }) + "BndJarImpl"
    val bndJarImplClass = bndClassLoader.loadClass(bndJarImplClassName)
    val bndJarImplCtr = bndJarImplClass.getConstructor(classOf[Seq[File]], classOf[Map[String, String]], classOf[File], classOf[Project])
    bndJarImplCtr.newInstance(classpath, props, destFile, project)

  }

}

import aQute.lib.osgi.Builder

class BndJarImpl(
  classpath: Seq[File],
  props: Map[String, String],
  destFile: File,
  project: Project) {

  val builder = new Builder()

  classpath.foreach { file => builder.addClasspath(file) }
  props.foreach { case (key, value) => builder.setProperty(key, value) }

  val jar = builder.build
  jar.write(destFile)

}