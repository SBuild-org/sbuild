package de.tototec.sbuild

import org.scalatest.FunSuite
import java.io.File
import de.tototec.sbuild.SchemeHandler.SchemeContext
import de.tototec.sbuild.internal.BuildFileProject

class ZipSchemeHandlerTest extends FunSuite {

  val projDir = Path.normalize(new File("target/test-output/ZipSchemeHandlerTest"))

  projDir.mkdirs
  val dummyProjectFile = new File(projDir, "SBuild.scala")
  dummyProjectFile.createNewFile()
  implicit val dummyProject: Project = new BuildFileProject(dummyProjectFile, null)

  val httpPath = Path(".sbuild/http")
  val zipPath = Path(".sbuild/unzip")

  SchemeHandler("http", new HttpSchemeHandler(httpPath))

  val zipHandler = new ZipSchemeHandler(zipPath)

  test("Base dir is correct") {
    assert(zipHandler.baseDir === zipPath)
  }

  test("Bad path with missing key=value pair") {
    intercept[ProjectConfigurationException] {
      zipHandler.localPath(SchemeContext("zip", "badPath"))
    }
  }

  test("Bad path with unsupported key=value pair") {
    intercept[ProjectConfigurationException] {
      zipHandler.localPath(SchemeContext("zip", "badKey=badValue"))
    }
  }

  test("Bad path with missing value in key=value pair") {
    intercept[ProjectConfigurationException] {
      zipHandler.localPath(SchemeContext("zip", "file"))
    }
  }

  test("Valid path without nested scheme") {
    assert(zipHandler.localPath(SchemeContext("zip", "file=/content.jar;archive=test.zip")) === "file:" + zipPath.getPath + "/8caba7d65b81501f3b65eca199c28ace/content.jar")
  }

  test("Valid path with explicit file scheme") {
    assert(zipHandler.localPath(SchemeContext("zip", "file=/content.jar;archive=file:test.zip")) === "file:" + zipPath + "/64435b5ec7279a1857b506ab0cdd344e/content.jar")
  }

  test("Valid path with nested http scheme") {
    assert(zipHandler.localPath(SchemeContext("zip", "file=/content.jar;archive=http://example.org/test.zip")) === "file:" + zipPath.getPath + "/81ce359bcbbb6f271516cb8dd272cb25/content.jar")
  }

  test("Valid path with nested http scheme and renamed file") {
    assert(zipHandler.localPath(SchemeContext("zip", "file=content.jar;targetFile=renamed-content.jar;archive=http://example.org/test.zip")) === "file:" + projDir + "/renamed-content.jar")
  }

}