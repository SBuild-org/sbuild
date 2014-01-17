package de.tototec.sbuild

import org.scalatest.FunSuite
import java.io.File
import de.tototec.sbuild.SchemeHandler.SchemeContext
import de.tototec.sbuild.internal.BuildFileProject
import org.scalatest.FreeSpec

class ZipSchemeHandlerTest extends FreeSpec {

  val projDir = Path.normalize(new File("target/test-output/ZipSchemeHandlerTest"))

  projDir.mkdirs
  val dummyProjectFile = new File(projDir, "SBuild.scala")
  dummyProjectFile.createNewFile()
  implicit val dummyProject: Project = new BuildFileProject(dummyProjectFile, null)

  val httpPath = Path(".sbuild/http")
  val zipPath = Path(".sbuild/unzip")

  SchemeHandler("http", new HttpSchemeHandler(httpPath))

  val zipHandler = new ZipSchemeHandler(zipPath)

  "Base dir is correct" in {
    assert(zipHandler.baseDir === zipPath)
  }

  "Bad path with missing key=value pair" in {
    intercept[ProjectConfigurationException] {
      zipHandler.localPath(SchemeContext("zip", "badPath"))
    }
  }

  "Bad path with unsupported key=value pair" in {
    intercept[ProjectConfigurationException] {
      zipHandler.localPath(SchemeContext("zip", "badKey=badValue"))
    }
  }

  "Bad path with missing value in key=value pair" in {
    intercept[ProjectConfigurationException] {
      zipHandler.localPath(SchemeContext("zip", "file"))
    }
  }

  "Valid path without nested scheme" in {
    assert(zipHandler.localPath(SchemeContext("zip", "file=/content.jar;archive=test.zip")) === "file:" + zipPath.getPath + "/8caba7d65b81501f3b65eca199c28ace/content.jar")
  }

  "Valid path with explicit file scheme" in {
    assert(zipHandler.localPath(SchemeContext("zip", "file=/content.jar;archive=file:test.zip")) === "file:" + zipPath + "/64435b5ec7279a1857b506ab0cdd344e/content.jar")
  }

  "Valid path with nested http scheme" in {
    assert(zipHandler.localPath(SchemeContext("zip", "file=/content.jar;archive=http://example.org/test.zip")) === "file:" + zipPath.getPath + "/81ce359bcbbb6f271516cb8dd272cb25/content.jar")
  }

  "Valid path with nested http scheme and renamed file" in {
    assert(zipHandler.localPath(SchemeContext("zip", "file=content.jar;targetFile=renamed-content.jar;archive=http://example.org/test.zip")) === "file:" + projDir + "/renamed-content.jar")
  }
  
}