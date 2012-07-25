package de.tototec.sbuild

/**
 * Common superclass for specific SBuild exceptions. 
 */
class SBuildException(msg: String, cause: Throwable = null) extends RuntimeException(msg, cause)

/**
 * An invalid commandline was given.
 */
class InvalidCommandlineException(msg: String, cause: Throwable = null) extends SBuildException(msg, cause)

/**
 * The execution of an target, which was defined in the project file, failed.
 */
class ExecutionFailedException(msg: String, cause: Throwable = null) extends SBuildException(msg, cause)

/**
 * A error was detected while parsing and/or initializing the project.
 */
class ProjectConfigurationException(msg: String, cause: Throwable = null) extends SBuildException(msg, cause)

/**
 * An unsupported scheme was used in a target or dependency. 
 * Usual reasons are typos or forgotten scheme handler registrations.
 */
class UnsupportedSchemeException(msg: String, cause: Throwable = null) extends SBuildException(msg, cause)

/**
 * An unknown target was requested (on command line or as a dependency). 
 */
class TargetNotFoundException(msg: String, cause: Throwable = null) extends SBuildException(msg, cause)