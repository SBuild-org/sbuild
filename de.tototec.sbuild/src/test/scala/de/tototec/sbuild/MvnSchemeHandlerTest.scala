package de.tototec.sbuild

import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner
import java.io.File
import de.tototec.sbuild.SchemeHandler.SchemeContext

class MvnSchemeHandlerTest extends FunSuite {

  val basePath = "/target/cmvn-scheme-test"
  val cmvn = new MvnSchemeHandler(new File(basePath))(null)

  test("local path test 1") {
    assert(cmvn.localPath(SchemeContext("mvn", "a:b:1")) === "file:" + basePath + "/a/b/1/b-1.jar")
  }

  test("local path test 2") {
    assert(cmvn.localPath(SchemeContext("mvn", "a:b:1;classifier=opt1")) === "file:" + basePath + "/a/b/1/b-1-opt1.jar")
  }

}