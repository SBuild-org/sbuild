package de.tototec.sbuild.compilerplugin

import scala.tools.nsc.Global
import scala.tools.nsc.Phase
import scala.tools.nsc.plugins.Plugin
import scala.tools.nsc.plugins.PluginComponent
import scala.tools.nsc.transform.Transform
import scala.tools.nsc.transform.InfoTransform
import java.io.File
import java.io.FileOutputStream
import java.io.BufferedOutputStream
import java.io.FileWriter
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.util.Properties

class AnalyzeTypesPlugin(override val global: Global) extends Plugin {
  import global._

  override val name = "analyzetypes"
  override val description = "Analyze Types and their source files"
  override val components = List[PluginComponent](Component)

  override val optionsHelp: Option[String] = Some("  -P:analyzetypes:outfile    Set the output file.")

  private var outfile: Option[File] = None

  override def processOptions(options: List[String], error: String => Unit) {
    options.foreach {
      case option if option.startsWith("outfile=") =>
        val outfileName = option.substring("outfile=".length)
        val outfile = new File(outfileName).getAbsoluteFile()
        outfile.getParentFile() match {
          case null =>
          case parentFile => parentFile.mkdirs()
        }
        this.outfile = Some(outfile)

      case option => error("Unsupported option: " + option)

    }

  }

  private object Component extends PluginComponent {
    override val global: AnalyzeTypesPlugin.this.global.type = AnalyzeTypesPlugin.this.global
    override val runsAfter = List[String]("parser")
    override val phaseName = AnalyzeTypesPlugin.this.name

    override def newPhase(_prev: Phase) = new AnalyzeTypesPhase(_prev)

    class AnalyzeTypesPhase(prev: Phase) extends StdPhase(prev) {

      override def name = AnalyzeTypesPlugin.this.name
      val outfile = AnalyzeTypesPlugin.this.outfile

      override def apply(unit: CompilationUnit) {

        //        val writer = new {
        //          val writer = outfile.map { file =>
        //            val stream = new FileOutputStream(file, true)
        //            val writer = new OutputStreamWriter(stream, "8859_1" /* same as properties files */ )
        //            new BufferedWriter(writer)
        //          }
        //          val path = unit.source.file.file.getPath()
        //          def append(className: String) {
        //            writer.map { w =>
        //              w.write(className + "=" + path)
        //              w.newLine()
        //            }
        //          }
        //          def close { writer.map { _.close() } }
        //        }

        def findClasses(tree: Tree, seenPackage: List[String]): List[String] = {

          def unpackQualifier(qualifier: Tree): String = qualifier match {
            case EmptyTree => ""
            case Ident(name) => name.toString
            case Select(subTree, name) => unpackQualifier(subTree) match {
              case "" => name.toString
              case prefix => prefix + "." + name
            }
          }

          def goDeeper(name: String, subTrees: List[Tree]): List[String] = {
            val pack = name match {
              case "<empty>" => seenPackage
              case name => name :: seenPackage
            }
            subTrees.flatMap(subTree => findClasses(subTree, pack))
          }

          tree match {
            case tree @ PackageDef(pid, stats) =>
              // println("qualifier: " + scala.reflect.runtime.universe.showRaw(pid.qualifier))
              goDeeper(unpackQualifier(pid), stats)
            case tree @ ClassDef(modifiers, name, tParams, template) if name.toString != "$anon" =>
              (name.toString :: seenPackage).reverse.mkString(".") :: goDeeper(name.toString, List(template))
            case tree @ ModuleDef(modifiers, name, template) =>
              (name.toString + "$" :: seenPackage).reverse.mkString(".") :: goDeeper(name.toString, List(template))
            case _ => List()
          }
        }

        val props = new Properties()
        val path = unit.source.file.file.getPath()

        findClasses(unit.body, List()).foreach { className =>
          // writer.append(className)
          props.setProperty(className, path)
        }

        outfile.map { file =>
          val outStream = new BufferedOutputStream(new FileOutputStream(file, true))
          try {
            props.store(outStream, null)
          } finally {
            outStream.close()
          }
        }

        //        println("All full qualified classes in file: " + unit.source.file.file.getPath() + "\n" +
        //        findClasses(unit.body, List()).mkString("\n")
        //        )

      }
    }

  }

}
