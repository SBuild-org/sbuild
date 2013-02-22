package de.tototec.sbuild.embedded

import de.tototec.sbuild.Project
import java.io.File
import de.tototec.sbuild.TargetNotFoundException
import java.io.FileWriter
import scala.util.Failure
import org.scalatest.FlatSpec
import org.scalatest.FreeSpec
import scala.util.Failure
import de.tototec.sbuild.ProjectConfigurationException
import java.io.FileNotFoundException

class TestProjectEmbeddedResolver extends FreeSpec {

  "An empty project" - {

    "should not resolve a phony:test target" in {
      val projectFile = new File("target/test/TestProjectEmbeddedResolver/SBuild.scala")
      projectFile.getParentFile.mkdirs
      val writer = new FileWriter(projectFile)
      writer.write("// Dummy File")
      writer.close
      val project = new Project(projectFile)
      val resolver = new ProjectEmbeddedResolver(project)
      val result = resolver.resolve("phony:test", new NullProgressMonitor())
      assert(result.isFailure)
      assert(result.asInstanceOf[Failure[_]].exception.getClass === classOf[TargetNotFoundException])
    }

    "should not resolve a test target" - {

      "which does not exists as file" in {
        val projectFile = new File("target/test/TestProjectEmbeddedResolver/SBuild.scala")
        projectFile.getParentFile.mkdirs
        val writer = new FileWriter(projectFile)
        writer.write("// Dummy File")
        writer.close

        // ensure, the target file does not exists
        new File(projectFile.getParentFile, "test").delete

        val project = new Project(projectFile)
        val resolver = new ProjectEmbeddedResolver(project)
        val result = resolver.resolve("test", new NullProgressMonitor())
        assert(result.isFailure)
        assert(result.asInstanceOf[Failure[_]].exception.getClass === classOf[TargetNotFoundException])
      }

      "but should not fail when the target file exists" in {
        val projectFile = new File("target/test/TestProjectEmbeddedResolver/SBuild.scala")
        projectFile.getParentFile.mkdirs
        val writer = new FileWriter(projectFile)
        writer.write("// Dummy File")
        writer.close

        // create the file with same name as the target
        val writer2 = new FileWriter(new File(projectFile.getParentFile, "test"))
        writer2.write("// Dummy test file")
        writer2.close

        val project = new Project(projectFile)
        val resolver = new ProjectEmbeddedResolver(project)
        val result = resolver.resolve("test", new NullProgressMonitor())
        assert(result.isSuccess)
        assert(result.get === Seq(new File(projectFile.getParentFile.getAbsoluteFile, "test")))
      }

    }
  }

  "A project with one phony target" - {
    "should resolve that same target" in pending
  }
  
  "A project with one file target" - {
    "should resolve one file if it exists" in pending
  }
}