package de.tototec.sbuild.runner

import org.scalatest.FunSuite
import scala.util.Properties
import java.util.Properties
import java.io.File

class ClasspathConfigTest extends FunSuite {

  test("Read from Properties") {

    val props = new Properties()
    props.setProperty("sbuildClasspath", "de.tototec.sbuild-0.1.3.jar");
    props.setProperty("compileClasspath", "/tmp/scala-library-2.9.2.jar;/tmp/scala-compiler-2.9.2.jar");
    props.setProperty("projectClasspath", "de.tototec.sbuild.ant-0.1.3.jar:/tmp/de.tototec.sbuild.addons-svn.jar")

    val config = ClasspathConfig.readFromProperties(new File("/home/sbuild/bin/sbuild-dir/lib"), props);

    assert(Array("/home/sbuild/bin/sbuild-dir/lib/de.tototec.sbuild-0.1.3.jar") === config.sbuildClasspath)
    assert(Array("/tmp/scala-library-2.9.2.jar", "/tmp/scala-compiler-2.9.2.jar") === config.compileClasspath)
    assert(Array("/home/sbuild/bin/sbuild-dir/lib/de.tototec.sbuild.ant-0.1.3.jar", "/tmp/de.tototec.sbuild.addons-svn.jar") === config.projectClasspath)
  }

}