package de.tototec.sbuild

case class ResolveResult(val wasUpToDate: Boolean, val error: Option[Throwable]) 
