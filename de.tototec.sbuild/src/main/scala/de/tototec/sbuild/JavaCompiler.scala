package de.tototec.sbuild

import java.io.File
import de.tototec.sbuild.runner.SBuild

class JavaCompiler extends SchemeHandler {
  override def localPath(path: String): String = {
    // "target/classes/**/*.class"
    "phony:compile"
  }
  override def resolve(path: String): Option[Throwable] = {
    // TODO: compile
    None
  }

  def calcFilesToBuild: Seq[String] = {
    // TODO: add up-to-date-check
    Util.recursiveListFiles("source/main/java", """.*\.java$""".r)
    //			
    //	if (forceBuild) {
    //			return javaFiles;
    //		}
    //
    //		final int prefixLength = sourcePath.length();
    //		int skippedFiles = 0;
    //
    //		for (String file : javaFiles) {
    //			long timestamp = new File(file).lastModified();
    //
    //			File target = new File(outputDir
    //					+ file.substring(prefixLength, file.length() - 5)
    //					+ ".class");
    //			// log.println(Level.TRACE, "Checking target file: " + target);
    //			if (target.exists()) {
    //				if (target.lastModified() >= timestamp) {
    //					skippedFiles++;
    //					continue;
    //				}
    //			}
    //			filesToBuild.add(file);
    //		}
    //
    //		if (skippedFiles > 0) {
    //			OutputConfig
    //					.verbose(
    //							"Compiling only changed files. Use 'forceBuild' option to disable that. Skipping ",
    //							skippedFiles, " unchanged source files of total ",
    //							javaFiles.size() + " files.");
    //			if (skippedFiles > 0 && skippedFiles < javaFiles.size()) {
    //				OutputConfig
    //						.verbose("You may potentially suffer from stale static constants in some untouched class files.");
    //			}
    //		}
    //		return filesToBuild;
  }

  def compile = {
    val javaCmd = "java"
    val outputDir = "target/classes"
    val sourcePath = "src/main/java"
    val source = "1.6"
    val target = "1.6"
    val encoding = "UTF-8"
    val javaFiles = calcFilesToBuild
    val showDeprecation = true

    SBuildRunner.verbose("Compiling " + javaFiles.size +
      " Java sources files to '" + outputDir + "'")

    val cp = Array("CLASSPATH", "CLASSPATH").mkString(":")

    var params = Array[String]()
    params ++= Array(javaCmd, "-source", source, "-target", target, "-encoding", encoding, "-sourcepath", sourcePath, "-d", outputDir)

    if (cp.length() > 0) {
      params ++= Array("-cp", cp)
    }
    if (showDeprecation) {
      params ++= Array("-deprecation")
    }
    params ++= javaFiles

    new File(outputDir).mkdirs

    import sys.process.Process

    val exitCode = Process(params) !

    if (exitCode != 0) throw new SBuildException("An error occured while compiling the Java source code")

    //		final int result = JavaUtils.exec(compilerName, params);
    //		OutputConfig.info("Successfully compiled Java classes.");

    //		if (result != 0) {
    //			throw new RuntimeException(
    //					"An error occured while compiling the Java source code.");
    //		}
  }

}