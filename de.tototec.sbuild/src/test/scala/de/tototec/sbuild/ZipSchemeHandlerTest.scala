package de.tototec.sbuild

import org.scalatest.FunSuite
import java.io.File

class ZipSchemeHandlerTest extends FunSuite {

  val baseDir = new File("target/test-output/ZipSchemeHandlerTest").getAbsoluteFile().getCanonicalPath()
  new File(baseDir).mkdirs
  val dummyProjectFile = new File(baseDir, "SBuild.scala")
  dummyProjectFile.createNewFile()
  val dummyProject = new Project(dummyProjectFile, null)
  SchemeHandler("http", new HttpSchemeHandler(new File(baseDir))(dummyProject))(dummyProject)

  val zipHandler = new ZipSchemeHandler(new File(baseDir))(dummyProject)

  test("Bad path with missing key=value pair") {
    intercept[ProjectConfigurationException] {
      zipHandler.localPath("badPath")
    }
  }

  test("Bad path with unsupported key=value pair") {
    intercept[ProjectConfigurationException] {
      zipHandler.localPath("badKey=badValue")
    }
  }

  test("Bad path with missing value in key=value pair") {
    intercept[ProjectConfigurationException] {
      zipHandler.localPath("file")
    }
  }

  test("Valid path without nested scheme") {
    assert(zipHandler.localPath("file=/content.jar;archive=test.zip") === "file:" + baseDir + "/content.jar")
  }

  test("Valid path with explicit file scheme") {
    assert(zipHandler.localPath("file=/content.jar;archive=file:test.zip") === "file:" + baseDir + "/content.jar")
  }

  test("Valid path with nested http scheme") {
    assert(zipHandler.localPath("file=/content.jar;archive=http://example.org/test.zip") === "file:" + baseDir + "/content.jar")
  }

  test("Valid path with nested http scheme and renamed file") {
    assert(zipHandler.localPath("file=content.jar;targetFile=renamed-content.jar;archive=http://example.org/test.zip") === "file:" + baseDir + "/renamed-content.jar")
  }

}