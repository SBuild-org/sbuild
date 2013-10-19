package de.tototec.sbuild.addons.bnd

import java.io.File
import java.net.URLClassLoader

import de.tototec.sbuild.Logger
import de.tototec.sbuild.Project

/**
 * Companion object for [[BndJar]], which creates OSGi bundles.
 *
 * Use [[BndJar$#apply]] to configure and execute it on one go.
 *
 */
object BndJar {

  /**
   * Creates, configure and execute the BndJar addon.
   *
   * For parameter documentation see the [[BndJar]] constructor.
   *
   */
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

/**
 * Create OSGi Bundles based on instructions processed by the [[http://www.aqute.biz/Bnd/Bnd Bundle Tools (bnd)]].
 *
 * To easily configure and execute the BndJar addon in one go, see [[BndJar$#apply]].
 *
 * '''Example:'''
 * {{{
 * class SBuild(implicit _project: Project) {
 *   // Your compile dependencies
 *   val compileCp = ...
 *
 *   // The bndlib jar
 *   val bndCp = "mvn:biz.aQute:bndlib:1.50.0"
 *
 *   // your bundle jar
 *   val jar = "target/org.example.bundle-1.0.0.jar"
 *
 *   Target(jar) dependsOn "compile" ~ compileCp ~ bndCp ~ "scan:target/classes" exec { ctx: TargetContext =>
 *     addons.bnd.BndJar(
 *       bndClasspath = bndCp.files,
 *       classpath = compileCp.files ++ Seq(Path("target/classes")),
 *       destFile = ctx.targetFile.get,
 *       props = Map(
 *         "Bundle-SymbolicName" -> "org.example.bundle",
 *         "Bundle-Version" -> "1.0.0",
 *         "Import-Package" -> "*",
 *         "Export-Package" -> """org.example.bundle;version="${Bundle-Version}"""",
 *         "Include-Resource" -> "src/main/resources")
 *     )
 *   }
 * }
 * }}}
 *
 * @constructor
 * Creates a new BndJar instance. All parameters can be omitted and set later.
 *
 * @param bndClasspath The Classpath which contains the `bndlib` and its dependencies. (E.g. bndlib.jar)
 * If not specified, bndlib will be loaded from SBuilds classpath, in which case, you have to add it e.g. with {{{ @classpath("mvn:biz.aQute:bndlib:1.50.0") }}}
 * @param classpath The classpath where bnd will search classes. Depending on the instructions, those classes will be included in the resulting bundle JAR or used to infer a reasonable version constraint for the import statements.
 * @param props Proerties containing the bnd instructions. For a complete reference of bnd instructions, refer the bnd project page at [[http://www.aqute.biz/Bnd/Bnd]].
 * @param destFile The resulting JAR file.
 *
 */
class BndJar(
    var bndClasspath: Seq[File] = null,
    var classpath: Seq[File] = null,
    var props: Map[String, String] = null,
    var destFile: File = null)(implicit project: Project) {

  private[this] val log = Logger[BndJar]

  def execute {

    val bndClassLoader = bndClasspath match {
      case null => classOf[BndJar].getClassLoader
      case cp =>
        val cl = new URLClassLoader(cp.map { f => f.toURI().toURL() }.toArray, classOf[BndJar].getClassLoader)
        log.debug("Using addional bnd classpath: " + cl.getURLs().mkString(", "))
        cl
    }

    val (builderClass, jarClass) = try {
      val builderClassName = "aQute.lib.osgi.Builder"
      val builderClass = bndClassLoader.loadClass(builderClassName)
      val jarClassName = "aQute.lib.osgi.Jar"
      val jarClass = bndClassLoader.loadClass(jarClassName)
      (builderClass, jarClass)
    } catch {
      case e: ClassNotFoundException =>
        val builderClassName = "aQute.bnd.osgi.Builder"
        log.debug(s"""Could not found class "${e.getMessage}". Trying new package name (since 2.0.0) "aQute.bnd.ogsi".""")
        val builderClass = bndClassLoader.loadClass(builderClassName)
        val jarClassName = "aQute.bnd.osgi.Jar"
        val jarClass = bndClassLoader.loadClass(jarClassName)
        (builderClass, jarClass)
    }
    val builder = builderClass.newInstance

    val setBaseMethod = builderClass.getMethod("setBase", classOf[File])
    setBaseMethod.invoke(builder, project.projectDirectory)

    val addClasspathMethod = builderClass.getMethod("addClasspath", classOf[File])
    if (classpath != null) classpath.foreach { file =>
      addClasspathMethod.invoke(builder, file)
    }

    val setPropertyMethod = builderClass.getMethod("setProperty", classOf[String], classOf[String])
    if (props != null) props.foreach {
      case (key, value) => setPropertyMethod.invoke(builder, key, value)
    }

    val buildMethod = builderClass.getMethod("build")
    val jar = buildMethod.invoke(builder)

    val writeMethod = jarClass.getMethod("write", classOf[File])

    if (!destFile.isAbsolute()) destFile = destFile.getAbsoluteFile()

    val parentDir = destFile.getParentFile()
    if (parentDir != null && !parentDir.exists()) {
      log.debug("Create directory: " + parentDir)
      parentDir.mkdirs()
    }

    writeMethod.invoke(jar, destFile)

  }

}
