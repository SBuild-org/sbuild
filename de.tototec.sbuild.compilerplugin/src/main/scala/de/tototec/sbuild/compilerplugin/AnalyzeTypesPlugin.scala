package de.tototec.sbuild.compilerplugin

import scala.tools.nsc.Global
import scala.tools.nsc.Phase
import scala.tools.nsc.plugins.Plugin
import scala.tools.nsc.plugins.PluginComponent
import scala.tools.nsc.transform.Transform
import scala.tools.nsc.transform.InfoTransform

class AnalyzeTypesPlugin(override val global: Global) extends Plugin {
  import global._

  override val name = "analyzetypes"
  override val description = "Analyze Types and their source files"
  override val components = List[PluginComponent](Component1)

  private object Component1 extends PluginComponent {
    override val global: AnalyzeTypesPlugin.this.global.type = AnalyzeTypesPlugin.this.global
    override val runsAfter = List[String]("parser")
    override val phaseName = AnalyzeTypesPlugin.this.name

    override def newPhase(_prev: Phase) = new AnalyzeTypesPhase(_prev)

    class AnalyzeTypesPhase(prev: Phase) extends StdPhase(prev) {

      override def name = AnalyzeTypesPlugin.this.name

      override def apply(unit: CompilationUnit) {

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
              println("qualifier: " + scala.reflect.runtime.universe.showRaw(pid.qualifier))
              goDeeper(unpackQualifier(pid), stats)
            case tree @ ClassDef(modifiers, name, tParams, template) if name.toString != "$anon" =>
              (name.toString :: seenPackage).reverse.mkString(".") :: goDeeper(name.toString, List(template))
            case tree @ ModuleDef(modifiers, name, template) =>
              (name.toString :: seenPackage).reverse.mkString(".") :: goDeeper(name.toString, List(template))
            case _ => List()
          }
        }

        println("All full qualified classes in file: " + unit.source.file.file.getPath() + "\n" +
          findClasses(unit.body, List()).mkString("\n")
        )

      }
    }

  }

}
