package de.tototec.sbuild.natures.experimental

import de.tototec.sbuild._
import de.tototec.sbuild.TargetRefs._
import de.tototec.sbuild.ant._
import de.tototec.sbuild.ant.tasks._

/**
 * Natures depends at least on SBuild 0.3.2 or above.
 *
 * To ensure, that it is always obvious where a def/val comes from, some naming policy is required.
 * Without, users would very fast have the feeling of magic and uncertainty.
 *
 * Also, as a best practice, when configuring your mixed natures,
 * you should always add the optional "overwrite" keyword when you intend to overwrite something.
 * That way, the compile will understand your intend and can give a meaningful error message,
 * if for some reason there is no such def to override.
 *
 * Naming policy: Each nature should only define new methods in its own namespace.
 * Example: The Nature "MyOwnNature" should create all def's with the prefix "myOwn_", the "Nature" suffix should not be part of it.
 * Of course, "myOwn" would be also ok as a name, if only one def is needed and the name is already self describing,
 * like e.g. "OutputDirNature" and the def "outputDir".
 *
 */
trait Nature {

  /**
   * Create target(s) in the scope of the given (implicit) project.
   * Any implementation has to take care of calling super.createTargets.
   */
  def createTargets(implicit project: Project): Seq[Target] = Seq()

}

/**
 * Basic nature, configuring an output directory.
 *
 * @see CleanNature
 */
trait OutputDirNature extends Nature {
  /**
   * The primary output directory.
   */
  def outputDir: String = "target"
}

trait ClassesDirNature extends OutputDirNature {
  def classesDir: String = outputDir + "/clases"
}

/**
 * Configures a concrete artifact which has a name and a version.
 */
trait ArtifactNature extends Nature {
  def artifact_name: String
  def artifact_version: String
}

/**
 * Configures a artifactGroup, which might be required in some Maven-compatibility situations.
 */
trait ArtifactGroupNature extends Nature {
  def artifactGroup: String
}

trait CleanNature extends OutputDirNature {
  def clean_targetName: String = "clean"
  abstract override def createTargets(implicit sbuildProject: Project): Seq[Target] = super.createTargets(sbuildProject) ++
    Seq(Target("phony:" + clean_targetName) exec {
      AntDelete(dir = Path(outputDir))
    })
}

trait JavaSourcesNature {
  def javaSources_sources: Seq[String] = Seq("src/main/java")
  def javaSources_encoding: String = "UTF-8"
  def javaSources_source: Option[String] = None
}

trait CompileJavaNature extends JavaSourcesNature with ClassesDirNature {
  def compileJava_targetName: String = "compileJava"
  def compileJava_outputDir: String = classesDir
  def compileJava_classpath: TargetRefs = TargetRefs()
  def compileJava_extraDependsOn: TargetRefs = new TargetRefs()
  def compileJava_extraSources: Seq[String] = Seq()
  def compileJava_target: Option[String] = None

  abstract override def createTargets(implicit sbuildProject: Project): Seq[Target] = {

    val sources = Pathes(javaSources_sources ++ compileJava_extraSources)

    val compile = Target("phony:" + compileJava_targetName) dependsOn compileJava_extraDependsOn exec { ctx: TargetContext =>
      IfNotUpToDate(sources ++ ctx.fileDependencies, Path(outputDir), ctx) {
        AntMkdir(dir = Path(compileJava_outputDir))
        AntJavac(
          // TODO: add more
          source = javaSources_source.getOrElse(null),
          target = compileJava_target.getOrElse(null),
          encoding = javaSources_encoding,
          classpath = AntPath(locations = ctx.fileDependencies),
          destDir = Path(compileJava_outputDir),
          srcDir = AntPath(locations = sources.filter(_.exists))
        )
      }
    }

    super.createTargets(sbuildProject) ++ Seq(compile)
  }
}

trait Java6CompilerNature extends CompileJavaNature {
  override def javaSources_source = Some("1.6")
  override def compileJava_target = Some("1.6")
}

trait ScalaSourcesNature extends Nature {
  def scalaSources_sources: Seq[String] = Seq("src/main/scala", "src/main/java")
  def scalaSources_encoding: String = "UTF-8"
}

trait CompileScalaNature extends ScalaSourcesNature with ClassesDirNature {
  def compileScala_targetName: String = "compileScala"
  def compileScala_outputDir: String = classesDir
  def compileScala_extraDependsOn: TargetRefs = TargetRefs()
  def compileScala_extraSources: Seq[String] = Seq()
  def compileScala_debugInfo: String = "vars"
  def compileScala_target: Option[String] = None
  def compileScala_deprecation: Boolean = true
  def compileScala_uncecked: Boolean = true
  def compileScala_classpath: TargetRefs = TargetRefs()
  def compileScala_scalaVersion: String = "2.10.0"
  def compileScala_compilerClasspath: Option[TargetRefs] = None

  abstract override def createTargets(implicit sbuildProject: Project) = {

    val Scala27 = """(2\.7\..*)""".r
    val Scala28 = """(2\.8\..*)""".r
    val Scala29 = """(2\.9\..*)""".r
    val Scala210 = """(2\.10\..*)""".r
    val Scala211 = """(2\.11\..*)""".r

    import de.tototec.sbuild.TargetRefs._

    val compilerClasspath = compileScala_compilerClasspath match {
      case Some(cp) => cp
      case None =>
        compileScala_scalaVersion match {
          case Scala27(_) | Scala28(_) | Scala29(_) =>
            s"mvn:org.scala-lang:scala-library:${compileScala_scalaVersion}" ~
              s"mvn:org.scala-lang:scala-compiler:${compileScala_scalaVersion}"
          case Scala210(v) =>
            s"mvn:org.scala-lang:scala-library:${compileScala_scalaVersion}" ~
              s"mvn:org.scala-lang:scala-compiler:${compileScala_scalaVersion}" ~
              s"mvn:org.scala-lang:scala-reflect:${compileScala_scalaVersion}"
          case Scala211(v) =>
            // not exactly sure about future compiler classpath and sources
            s"mvn:org.scala-lang:scala-library:${compileScala_scalaVersion}" ~
              s"mvn:org.scala-lang:scala-compiler:${compileScala_scalaVersion}" ~
              s"mvn:org.scala-lang:scala-reflect:${compileScala_scalaVersion}"
          case _ =>
            val ex = new ProjectConfigurationException(s"CompileScalaNature: Unsupported Scala version ${compileScala_scalaVersion} specified.")
            ex.buildScript = Option(sbuildProject.projectFile)
            throw ex
        }

    }

    super.createTargets(sbuildProject) ++
      Seq(
        Target("phony:" + compileScala_targetName) dependsOn
          compilerClasspath ~ compileScala_classpath ~ compileScala_extraDependsOn exec
          { ctx: TargetContext =>

            val sources = Pathes(scalaSources_sources ++ compileScala_extraSources)

            IfNotUpToDate(sources ++ ctx.fileDependencies, Path(outputDir), ctx) {
              AntMkdir(dir = Path(compileScala_outputDir))
              addons.scala.Scalac(
                target = compileScala_target.getOrElse(null),
                encoding = scalaSources_encoding,
                deprecation = compileScala_deprecation,
                unchecked = compileScala_uncecked,
                debugInfo = compileScala_debugInfo,
                fork = true,
                destDir = Path(compileScala_outputDir),
                srcDirs = sources,
                compilerClasspath = compilerClasspath.files,
                classpath = compileScala_classpath.files
              )
            }

          }
      )
  }
}

trait JarResourcesNature {
  def jarResources: Seq[String] = Seq("src/main/resources")
}

trait JarNature extends OutputDirNature with ClassesDirNature with ArtifactNature with JarResourcesNature {
  def jar_output = outputDir + "/" + artifact_name + "-" + artifact_version + ".jar"
  def jar_dependsOn: TargetRefs = new TargetRefs()
  def jar_dirs: Seq[String] = Seq(classesDir) ++ jarResources

  abstract override def createTargets(implicit sbuildProject: Project) = {
    val jarTarget =
      Target(jar_output) dependsOn jar_dependsOn exec {
        AntJar(
          destFile = Path(jar_output),
          fileSets = jar_dirs.map { f => Path(f) }.filter(_.exists).map { p => AntFileSet(dir = p)
          }
        )
      }
    var targets = Seq(jarTarget)

    super.createTargets(sbuildProject) ++ targets
  }
}

trait JavaSourcesJarNature extends OutputDirNature with ArtifactNature with JavaSourcesNature with JarResourcesNature {
  def javaSourcesJar_output = outputDir + "/" + artifact_name + "-" + artifact_version + "+sources.jar"
  def javaSourcesJar_dependsOn: TargetRefs = TargetRefs()
  def javaSourcesJar_dirs: Seq[String] = javaSources_sources ++ jarResources

  abstract override def createTargets(implicit sbuildProject: Project) = super.createTargets(sbuildProject) ++
    Seq(
      Target(javaSourcesJar_output) dependsOn javaSourcesJar_dependsOn exec {
        AntJar(
          destFile = Path(javaSourcesJar_output),
          fileSets = javaSourcesJar_dirs.map { f => Path(f) }.filter(_.exists).map { p => AntFileSet(dir = p)
          }
        )
      }
    )
}

trait ScalaSourcesJarNature extends OutputDirNature with ArtifactNature with ScalaSourcesNature with JarResourcesNature {
  def scalaSourcesJar_output = outputDir + "/" + artifact_name + "-" + artifact_version + "+sources.jar"
  def scalaSourcesJar_dependsOn: TargetRefs = TargetRefs()
  def scalaSourcesJar_dirs: Seq[String] = scalaSources_sources ++ jarResources

  abstract override def createTargets(implicit sbuildProject: Project) = super.createTargets(sbuildProject) ++
    Seq(
      Target(scalaSourcesJar_output) dependsOn scalaSourcesJar_dependsOn exec {
        AntJar(
          destFile = Path(scalaSourcesJar_output),
          fileSets = scalaSourcesJar_dirs.map { f => Path(f) }.filter(_.exists).map { p => AntFileSet(dir = p)
          }
        )
      }
    )
}

