package de.tototec.sbuild

class SBuildException(msg: String, cause: Throwable = null) extends RuntimeException(msg, cause)

class InvalidCommandlineException(msg: String, cause: Throwable = null) extends SBuildException(msg, cause)

class ExecutionFailedException(msg: String, cause: Throwable = null) extends SBuildException(msg, cause)

class ProjectConfigurationException(msg: String, cause: Throwable = null) extends SBuildException(msg, cause)

class UnsupportedSchemeException(msg: String, cause: Throwable = null) extends SBuildException(msg, cause)