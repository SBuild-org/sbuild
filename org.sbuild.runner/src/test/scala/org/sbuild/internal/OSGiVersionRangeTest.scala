package org.sbuild.internal

import org.scalatest.FreeSpec

class OSGiVersionRangeTest extends FreeSpec {

  import OSGiVersionRange._

  "OSGiVersionRange" - {

    "parseVersionRange" - {

      "should throw Exception for ''" in {
        intercept[IllegalArgumentException] {
          parseVersionRange("")
        }
      }

      "should throw Exception for '0.0.0'" in {
        intercept[IllegalArgumentException] {
          parseVersionRange("0.0.0")
        }
      }

      "should parse '[0.0.0,1.0.0]'" in {
        assert(parseVersionRange("[0.0.0,1.0.0]") ===
          new OSGiVersionRange(LeftClosed, OSGiVersion.emptyVersion, OSGiVersion.parseVersion("1.0.0"), RightClosed))
      }

    }

    "parseVersionOrRange" - {

      "should parse ''" in {
        assert(parseVersionOrRange("") ===
          new OSGiVersionRange(LeftClosed, OSGiVersion.parseVersion(""), null, RightOpen))
      }

      "should parse '0.0.0'" in {
        assert(parseVersionOrRange("") ===
          new OSGiVersionRange(LeftClosed, OSGiVersion.emptyVersion, null, RightOpen))
      }
    }

  }

}