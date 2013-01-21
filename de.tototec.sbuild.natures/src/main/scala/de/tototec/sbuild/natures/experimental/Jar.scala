package de.tototec.sbuild.natures.experimental

import de.tototec.sbuild._
import de.tototec.sbuild.TargetRefs._
import de.tototec.sbuild.ant._
import de.tototec.sbuild.ant.tasks._
import org.apache.tools.ant.types.FileSet

trait JarResourcesConfig {
  def jarResources: Seq[String] = Seq("src/main/resources")
}

trait JarConfig extends ArtifactConfig with ClassesDirConfig with JarResourcesConfig {

  def jar_output = outputDir + "/" + artifact_name + "-" + artifact_version + ".jar"

  /**
   * Additional dependency, most typical, you will want to add a compile target here.
   */
  def jar_dependsOn: TargetRefs = new TargetRefs()

  /**
   * Directories, whose content should be added to the jar file.
   */
  def jar_dirs: Seq[String] = Seq(classesDir) ++ jarResources

  /**
   * Additional Ant FileSet's refering to be added jar content.
   * @see de.tototec.sbuild.ant.AntFileSet
   */
  def jar_fileSets: Seq[FileSet] = Seq()

  def jar_manifestEntries: Map[String, String] = Map()

}

trait JarNature extends Nature { this: JarConfig with ProjectConfig =>
  abstract override def createTargets: Seq[Target] = {
    val jarTarget =
      Target(jar_output) dependsOn jar_dependsOn exec {
        AntJar(
          destFile = Path(jar_output),
          fileSets = jar_dirs.map { f => Path(f) }.filter(_.exists).map { p => AntFileSet(dir = p) } ++
            jar_fileSets,
          manifestEntries = jar_manifestEntries
        )
      }
    var targets = Seq(jarTarget)

    super.createTargets ++ targets
  }
}

trait JavaSourcesJarConfig extends OutputDirConfig with ArtifactConfig with JavaSourcesConfig with JarResourcesConfig {
  def javaSourcesJar_output = outputDir + "/" + artifact_name + "-" + artifact_version + "-sources.jar"
  def javaSourcesJar_dependsOn: TargetRefs = TargetRefs()
  def javaSourcesJar_dirs: Seq[String] = javaSources_sources ++ jarResources
  def javaSourcesJar_fileSets: Seq[FileSet] = Seq()
  def javaSourcesJar_manifestEntries: Map[String, String] = Map()
}

trait JavaSourcesJarNature extends Nature { this: JavaSourcesJarConfig with ProjectConfig =>
  abstract override def createTargets: Seq[Target] = super.createTargets ++
    Seq(
      Target(javaSourcesJar_output) dependsOn javaSourcesJar_dependsOn exec {
        AntJar(
          destFile = Path(javaSourcesJar_output),
          fileSets = javaSourcesJar_dirs.map { f => Path(f) }.filter(_.exists).map { p => AntFileSet(dir = p) } ++
            javaSourcesJar_fileSets,
          manifestEntries = javaSourcesJar_manifestEntries
        )
      }
    )
}

trait ScalaSourcesJarConfig extends OutputDirConfig with ArtifactConfig with ScalaSourcesConfig with JarResourcesConfig {
  def scalaSourcesJar_output = outputDir + "/" + artifact_name + "-" + artifact_version + "-sources.jar"
  def scalaSourcesJar_dependsOn: TargetRefs = TargetRefs()
  def scalaSourcesJar_dirs: Seq[String] = scalaSources_sources ++ jarResources
  def scalaSourcesJar_fileSets: Seq[FileSet] = Seq()
  def scalaSourcesJar_manifestEntries: Map[String, String] = Map()
}

trait ScalaSourcesJarNature extends Nature { this: ScalaSourcesJarConfig with ProjectConfig =>
  abstract override def createTargets: Seq[Target] = super.createTargets ++
    Seq(
      Target(scalaSourcesJar_output) dependsOn scalaSourcesJar_dependsOn exec {
        AntJar(
          destFile = Path(scalaSourcesJar_output),
          fileSets = scalaSourcesJar_dirs.map { f => Path(f) }.filter(_.exists).map { p => AntFileSet(dir = p) } ++
            scalaSourcesJar_fileSets,
          manifestEntries = scalaSourcesJar_manifestEntries
        )
      }
    )
}

