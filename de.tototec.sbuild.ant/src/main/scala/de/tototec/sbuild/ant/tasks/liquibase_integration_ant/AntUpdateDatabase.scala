package de.tototec.sbuild.ant.tasks.liquibase_integration_ant

import de.tototec.sbuild.Project
import liquibase.integration.ant.DatabaseUpdateTask
import de.tototec.sbuild.ant.AntProject
import java.io.File
import liquibase.integration.ant.BaseLiquibaseTask.ChangeLogProperty
import org.apache.tools.ant.types.Path

object AntUpdateDatabase {
  def apply(changeLogFile: String = null,
            driver: String = null,
            url: String = null,
            username: String = null,
            password: String = null,
            defaultSchemaName: String = null,
            outputFile: String = null,
            promptOnNonLocalDatabase: java.lang.Boolean = null,
            dropFirst: java.lang.Boolean = null,
            classpath: Path = null,
            contexts: String = null,
            currentDateTimeFunction: String = null,
            databaseChangeLogTableName: String = null,
            databaseChangeLogLockTableName: String = null,
            changeLogProperties: Map[String, String] = Map())(implicit _project: Project) =
    new AntUpdateDatabase(
      changeLogFile = changeLogFile,
      driver = driver,
      url = url,
      username = username,
      password = password,
      defaultSchemaName = defaultSchemaName,
      outputFile = outputFile,
      promptOnNonLocalDatabase = promptOnNonLocalDatabase,
      dropFirst = dropFirst,
      classpath = classpath,
      contexts = contexts,
      currentDateTimeFunction = currentDateTimeFunction,
      databaseChangeLogTableName = databaseChangeLogTableName,
      databaseChangeLogLockTableName = databaseChangeLogLockTableName,
      changeLogProperties = changeLogProperties
    ).execute
}

class AntUpdateDatabase()(implicit _project: Project) extends DatabaseUpdateTask {
  setProject(AntProject())

  def this(changeLogFile: String = null,
           driver: String = null,
           url: String = null,
           username: String = null,
           password: String = null,
           defaultSchemaName: String = null,
           outputFile: String = null,
           promptOnNonLocalDatabase: java.lang.Boolean = null,
           dropFirst: java.lang.Boolean = null,
           classpath: Path = null,
           contexts: String = null,
           currentDateTimeFunction: String = null,
           databaseChangeLogTableName: String = null,
           databaseChangeLogLockTableName: String = null,
           changeLogProperties: Map[String, String] = Map())(implicit _project: Project) {
    this
    if (changeLogFile != null) setChangeLogFile(changeLogFile)
    if (driver != null) setDriver(driver)
    if (url != null) setUrl(url)
    if (username != null) setUsername(username)
    if (password != null) setPassword(password)
    if (defaultSchemaName != null) setDefaultSchemaName(defaultSchemaName)
    if (outputFile != null) setOutputFile(outputFile)
    if (promptOnNonLocalDatabase != null) setPromptOnNonLocalDatabase(promptOnNonLocalDatabase.booleanValue)
    if (dropFirst != null) setDropFirst(dropFirst.booleanValue)
    if (classpath != null) createClasspath.add(classpath)
    if (contexts != null) setContexts(contexts)
    if (currentDateTimeFunction != null) setCurrentDateTimeFunction(currentDateTimeFunction)
    if (databaseChangeLogTableName != null) setDatabaseChangeLogTableName(databaseChangeLogTableName)
    if (databaseChangeLogLockTableName != null) setDatabaseChangeLogLockTableName(databaseChangeLogLockTableName)
    changeLogProperties.foreach {
      case (name, value) => addConfiguredChangeLogProperty(new ChangeLogProperty() {
        setName(name)
        setValue(value)
      })
    }
  }
}