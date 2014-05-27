package org.sbuild.runner

import org.scalatest.FunSuite
import scala.util.Properties
import java.util.Properties
import java.io.File

class ClasspathConfigTest extends FunSuite {

  test("Read from Properties") {

    val props = new Properties()
    props.setProperty("sbuildClasspath", "de.tototec.sbuild-0.1.3.jar");
    props.setProperty("compileClasspath", "scala-library-2.9.2.jar;scala-compiler-2.9.2.jar");
    props.setProperty("projectRuntimeClasspath", "de.tototec.sbuild.ant-0.1.3.jar:de.tototec.sbuild.addons-svn.jar")

    val config = new ClasspathConfig()
    config.readFromProperties(new File("/home/sbuild/bin/sbuild-dir/lib"), props);

    assert(Array(new File("/home/sbuild/bin/sbuild-dir/lib/de.tototec.sbuild-0.1.3.jar")) === config.sbuildClasspath)
    assert(Array(new File("/home/sbuild/bin/sbuild-dir/lib/scala-library-2.9.2.jar"), new File("/home/sbuild/bin/sbuild-dir/lib/scala-compiler-2.9.2.jar")) === config.compileClasspath)
    assert(Array(new File("/home/sbuild/bin/sbuild-dir/lib/de.tototec.sbuild.ant-0.1.3.jar"), new File("/home/sbuild/bin/sbuild-dir/lib/de.tototec.sbuild.addons-svn.jar")) === config.projectRuntimeClasspath)
  }

}