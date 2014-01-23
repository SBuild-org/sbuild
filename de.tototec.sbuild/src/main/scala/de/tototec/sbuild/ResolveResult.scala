package de.tototec.sbuild

@deprecated("No longer in use.", "0.9.1.9000")
case class ResolveResult(val wasUpToDate: Boolean, val error: Option[Throwable]) 
