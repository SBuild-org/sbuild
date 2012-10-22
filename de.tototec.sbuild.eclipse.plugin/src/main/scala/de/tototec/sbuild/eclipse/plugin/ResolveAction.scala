package de.tototec.sbuild.eclipse.plugin

case class ResolveAction(result: String, name: String, action: () => Boolean)
