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
      destFile = destFile
    ).execute

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

    val builderClassName = "aQute.lib.osgi.Builder"
    val builderClass = bndClassLoader.loadClass(builderClassName)
    val builder = builderClass.newInstance

    val setBaseMethod = builderClass.getMethod("setBase", classOf[File])
    setBaseMethod.invoke(builder, project.projectDirectory)

    val addClasspathMethod = builderClass.getMethod("addClasspath", classOf[File])
    classpath.foreach { file =>
      addClasspathMethod.invoke(builder, file)
    }

    val setPropertyMethod = builderClass.getMethod("setProperty", classOf[String], classOf[String])
    props.foreach {
      case (key, value) => setPropertyMethod.invoke(builder, key, value)
    }

    val buildMethod = builderClass.getMethod("build")
    val jar = buildMethod.invoke(builder)

    val jarClassName = "aQute.lib.osgi.Jar"
    val jarClass = bndClassLoader.loadClass(jarClassName)
    val writeMethod = jarClass.getMethod("write", classOf[File])

    writeMethod.invoke(jar, destFile)

  }

}
