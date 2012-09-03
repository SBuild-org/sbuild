package de.tototec.sbuild.addons.scalatest

import de.tototec.sbuild.Project
import java.io.File
import org.scalatest.tools.Runner

object ScalaTest {
  def apply(
    runPath: Seq[String] = null,
    reporter: String = null,
    configMap: Map[String, String] = null,
    includes: Seq[String] = null,
    excludes: Seq[String] = null)(implicit project: Project) =
    new ScalaTest(
      runPath = runPath,
      reporter = reporter,
      configMap = configMap,
      includes = includes,
      excludes = excludes
    ).execute

}

class ScalaTest()(implicit project: Project) {

  var runPath: Seq[String] = null
  var reporter: String = null
  var configMap: Map[String, String] = null
  var includes: Seq[String] = null
  var excludes: Seq[String] = null

  def this(
    runPath: Seq[String] = null,
    reporter: String = null,
    configMap: Map[String, String] = null,
    includes: Seq[String] = null,
    excludes: Seq[String] = null)(implicit project: Project) {
    this
    this.runPath = runPath
    this.reporter = reporter
    this.configMap = configMap
    this.includes = includes
    this.excludes = excludes
  }

  def execute {

    def whiteSpaceSeparated(seq: Seq[String]): String = seq.map(_.replaceAll(" ", "\\ ")).mkString(" ")

    var args = Array[String]()
    if (runPath != null) args ++= Array("-p", whiteSpaceSeparated(runPath))
    if (reporter != null) args ++= Array("-" + reporter)
    if (configMap != null) configMap foreach {
      case (key, value) => args ++= Array("-D" + key + "=" + value)
    }
    if (includes != null) args ++= Array("-n", whiteSpaceSeparated(includes))
    if (excludes != null) args ++= Array("-n", whiteSpaceSeparated(excludes))

    Runner.run(args)

  }

}
