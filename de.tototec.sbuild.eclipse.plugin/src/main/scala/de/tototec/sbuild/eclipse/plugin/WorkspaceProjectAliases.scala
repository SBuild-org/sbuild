package de.tototec.sbuild.eclipse.plugin

import org.eclipse.core.resources.ProjectScope
import org.eclipse.jdt.core.IJavaProject

object WorkspaceProjectAliases {
  def SBuildPreferencesNode = "de.tototec.sbuild.eclipse.plugin"
  def WorkspaceProjectAliasNode = "workspaceProjectAlias"

  def read(project: IJavaProject): Map[String, String] = {
    val projectScope = new ProjectScope(project.getProject)
    projectScope.getNode(SBuildPreferencesNode) match {
      case null =>
        debug("Could not access prefs node: " + SBuildPreferencesNode)
        Map()
      case prefs =>
        prefs.node(WorkspaceProjectAliasNode) match {
          case null =>
            debug("Could not access prefs node: " + WorkspaceProjectAliasNode)
            Map()
          case prefs =>
            val keys = prefs.keys
            debug("Found aliases in prefs for the following dependencies: " + keys.mkString(", "))
            keys.map {
              name => (name -> prefs.get(name, ""))
            }.filter {
              case (key, value) => value != ""
            }.toMap
        }
    }
  }
  
  def write(project: IJavaProject, aliases: Map[String,String]) {
    // TODO: write
  }
  
}