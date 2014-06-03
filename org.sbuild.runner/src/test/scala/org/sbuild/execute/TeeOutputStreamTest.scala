package org.sbuild.execute

import java.io.ByteArrayOutputStream
import java.io.PrintStream

import org.sbuild.execute.ContextAwareTeeOutputStream.TeeConfig
import org.scalatest.FreeSpec

class TeeOutputStreamTest extends FreeSpec {

  "Act as normal PrintStream" - {

    "Some output (reference)" in {
      val out = new ByteArrayOutputStream()
      val ps = new PrintStream(out)
      ps.println("line 1")
      ps.println("line 2")
      ps.close()
      assert(out.toString() === "line 1\nline 2\n")
    }

    "Some output" in {
      val out = new ByteArrayOutputStream()
      val tee = new ContextAwareTeeOutputStream(new PrintStream(out))
      tee.println("line 1")
      tee.println("line 2")
      tee.close()
      assert(out.toString() === "line 1\nline 2\n")
    }
  }

  "Act as partial tee" - {

    "Reference" in {
      val out = new ByteArrayOutputStream()
      val tee = new ContextAwareTeeOutputStream(new PrintStream(out))
      tee.println("line 1")
      tee.println("line 2")
      tee.println("line 3")
      tee.close()
      assert(out.toString() === "line 1\nline 2\nline 3\n")
    }

    "Capture all" in {
      val capture = new ByteArrayOutputStream()
      val capturePs = new PrintStream(capture)

      val out = new ByteArrayOutputStream()
      val tee = new ContextAwareTeeOutputStream(new PrintStream(out))
      val previous = ContextAwareTeeOutputStream.setConfig(tee, Some(TeeConfig(copyStream = capturePs)))
      tee.println("line 1")
      tee.println("line 2")
      tee.println("line 3")
      tee.close()

      capturePs.close()
      assert(out.toString() === "line 1\nline 2\nline 3\n")
      assert(capture.toString() === "line 1\nline 2\nline 3\n")
    }

    "Capture 'line 1'" in {
      val capture = new ByteArrayOutputStream()
      val capturePs = new PrintStream(capture)

      val out = new ByteArrayOutputStream()
      val tee = new ContextAwareTeeOutputStream(new PrintStream(out))

      val previous = ContextAwareTeeOutputStream.setConfig(tee, Some(TeeConfig(copyStream = capturePs)))
      tee.println("line 1")
      ContextAwareTeeOutputStream.setConfig(tee, None)

      tee.println("line 2")
      tee.println("line 3")
      tee.close()

      capturePs.close()
      assert(out.toString() === "line 1\nline 2\nline 3\n")
      assert(capture.toString() === "line 1\n")
    }

    "Capture 'line 2'" in {
      val capture = new ByteArrayOutputStream()
      val capturePs = new PrintStream(capture)

      val out = new ByteArrayOutputStream()
      val tee = new ContextAwareTeeOutputStream(new PrintStream(out))
      tee.println("line 1")

      ContextAwareTeeOutputStream.setConfig(tee, Some(TeeConfig(copyStream = capturePs)))
      tee.println("line 2")
      ContextAwareTeeOutputStream.setConfig(tee, None)

      tee.println("line 3")
      tee.close()

      capturePs.close()
      assert(out.toString() === "line 1\nline 2\nline 3\n")
      assert(capture.toString() === "line 2\n")
    }

    "Capture 'line 3'" in {
      val capture = new ByteArrayOutputStream()
      val capturePs = new PrintStream(capture)

      val out = new ByteArrayOutputStream()
      val tee = new ContextAwareTeeOutputStream(new PrintStream(out))
      tee.println("line 1")
      tee.println("line 2")

      ContextAwareTeeOutputStream.setConfig(tee, Some(TeeConfig(copyStream = capturePs)))
      tee.println("line 3")
      ContextAwareTeeOutputStream.setConfig(tee, None)

      tee.close()

      capturePs.close()
      assert(out.toString() === "line 1\nline 2\nline 3\n")
      assert(capture.toString() === "line 3\n")
    }

    "Capture 'line 1 + 3'" in {
      val capture = new ByteArrayOutputStream()
      val capturePs = new PrintStream(capture)

      val out = new ByteArrayOutputStream()
      val tee = new ContextAwareTeeOutputStream(new PrintStream(out))

      ContextAwareTeeOutputStream.setConfig(tee, Some(TeeConfig(copyStream = capturePs)))
      tee.println("line 1")
      ContextAwareTeeOutputStream.setConfig(tee, None)

      tee.println("line 2")

      ContextAwareTeeOutputStream.setConfig(tee, Some(TeeConfig(copyStream = capturePs)))
      tee.println("line 3")
      ContextAwareTeeOutputStream.setConfig(tee, None)

      tee.close()

      capturePs.close()
      assert(out.toString() === "line 1\nline 2\nline 3\n")
      assert(capture.toString() === "line 1\nline 3\n")
    }

    "Capture into alternating contexts" in {
      val capture1 = new ByteArrayOutputStream()
      val capture1Ps = new PrintStream(capture1)
      val capture1Config = TeeConfig(copyStream = capture1Ps)

      val capture2 = new ByteArrayOutputStream()
      val capture2Ps = new PrintStream(capture2)
      val capture2Config = TeeConfig(copyStream = capture2Ps)

      val out = new ByteArrayOutputStream()
      val tee = new ContextAwareTeeOutputStream(new PrintStream(out))

      ContextAwareTeeOutputStream.setConfig(tee, Some(TeeConfig(copyStream = capture1Ps)))
      tee.println("line 1")
      ContextAwareTeeOutputStream.setConfig(tee, Some(TeeConfig(copyStream = capture2Ps)))
      tee.println("line 2")
      ContextAwareTeeOutputStream.setConfig(tee, Some(TeeConfig(copyStream = capture1Ps)))
      tee.println("line 3")

      tee.close()
      capture1Ps.close()
      capture2Ps.close()

      assert(out.toString() === "line 1\nline 2\nline 3\n")
      assert(capture1.toString() === "line 1\nline 3\n")
      assert(capture2.toString() === "line 2\n")
    }

  }

}
