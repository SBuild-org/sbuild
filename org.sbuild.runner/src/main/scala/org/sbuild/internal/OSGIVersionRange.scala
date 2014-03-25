package org.sbuild.internal

import java.util.NoSuchElementException
import java.util.StringTokenizer

object OSGiVersionRange {
  /**
   * The left endpoint is open and is excluded from the range.
   *
   * The value of `LeftOpen` is `(`.
   */
  val LeftOpen = '('
  /**
   * The left endpoint is closed and is included in the range.
   * <p>
   * The value of {@code LEFT_CLOSED} is {@code '['}.
   */
  val LeftClosed = '['
  /**
   * The right endpoint is open and is excluded from the range.
   * <p>
   * The value of {@code RIGHT_OPEN} is {@code ')'}.
   */
  val RightOpen = ')'
  /**
   * The right endpoint is closed and is included in the range.
   * <p>
   * The value of {@code RIGHT_CLOSED} is {@code ']'}.
   */
  val RightClosed = ']'

  val LeftOpenDelimiter = LeftOpen.toString
  val LeftClosedDelimiter = LeftClosed.toString
  val LeftDelimiters = LeftClosedDelimiter + LeftOpenDelimiter
  val RightOpenDelimiter = RightOpen.toString
  val RightClosedDelimiter = RightClosed.toString
  val RightDelimiters = RightOpenDelimiter + RightClosedDelimiter
  val EndpointDelimiter = ","

  /**
   * Creates a version range from the specified string.
   *
   * Version range string grammar:
   *
   * <pre>
   * range ::= interval | atleast
   * interval ::= ( '[' | '(' ) left ',' right ( ']' | ')' )
   * left ::= version
   * right ::= version
   * atleast ::= version
   * </pre>
   *
   * @param range String representation of the version range. The versions in
   *        the range must contain no whitespace. Other whitespace in the
   *        range string is ignored.
   * @throws IllegalArgumentException If `range` is improperly formatted.
   */
  def parseVersionRange(range: String): OSGiVersionRange = try {
    val st = new StringTokenizer(range, LeftDelimiters, true)
    var token = st.nextToken().trim() // whitespace or left delim
    if (token.length() == 0) { // leading whitespace
      token = st.nextToken() // left delim
    }
    val closedLeft = LeftClosedDelimiter.equals(token)
    if (!closedLeft && !LeftOpenDelimiter.equals(token)) {
      // first token is not a delimiter, so it must be "atleast"
      if (st.hasMoreTokens()) { // there must be no more tokens
        throw new IllegalArgumentException("invalid range \"" + range + "\": invalid format")
      }
      new OSGiVersionRange(true, parseVersion(token, range), null, false)
    }
    var version = st.nextToken(EndpointDelimiter)
    val endpointLeft = parseVersion(version, range)
    token = st.nextToken(); // consume comma
    version = st.nextToken(RightDelimiters)
    token = st.nextToken(); // right delim
    val closedRight = RightClosedDelimiter.equals(token)
    if (!closedRight && !RightOpenDelimiter.equals(token)) {
      throw new IllegalArgumentException("invalid range \"" + range + "\": invalid format")
    }
    val endpointRight = parseVersion(version, range)

    if (st.hasMoreTokens()) { // any more tokens have to be whitespace
      token = st.nextToken("").trim()
      if (token.length() != 0) { // trailing whitespace
        throw new IllegalArgumentException("invalid range \"" + range + "\": invalid format")
      }
    }
    new OSGiVersionRange(closedLeft, endpointLeft, endpointRight, closedRight)

  } catch {
    case e: NoSuchElementException =>
      val iae = new IllegalArgumentException("invalid range \"" + range + "\": invalid format")
      iae.initCause(e)
      throw iae
  }

  /**
   * Parse version component into a Version.
   *
   * @param version version component string
   * @param range Complete range string for exception message, if any
   * @return Version
   */
  def parseVersion(version: String, range: String): OSGiVersion = {
    try {
      OSGiVersion.parseVersion(version)
    } catch {
      case e: IllegalArgumentException =>
        val iae = new IllegalArgumentException("invalid range \"" + range + "\": " + e.getMessage())
        iae.initCause(e)
        throw iae
    }
  }

}

/**
 * Version range. A version range is an interval describing a set of [[Version versions]].
 *
 * A range has a left (lower) endpoint and a right (upper) endpoint. Each
 * endpoint can be open (excluded from the set) or closed (included in the set).
 *
 * `OSGIVersionRange` objects are immutable.
 *
 * @constructor
 * @param left The left endpoint of this version range.
 * @param right  The right endpoint of this version range.
 *   May be `null` which indicates the right  endpoint is _Infinity_.
 *
 */
class OSGiVersionRange private (private val leftClosed: Boolean, val left: OSGiVersion, val right: OSGiVersion, private val rightClosed: Boolean) {

  import OSGiVersionRange._

  /*
  *  Whether this version range is empty. A version range is empty if
  *  the set of versions defined by the interval is empty.
  */
  val empty = right match {
    case null => false // infinity
    case _ => left.compareTo(right) match {
      case 0 => // endpoints equal
        !leftClosed || !rightClosed
      case c => c > 0 // true if left > right
    }
  }

  /* 
 * Creates a version range from the specified versions.
 * 
 * @param leftType Must be either [[OSGiVersioRange#LeftClosed]] or [[OSGiVersioRange#LeftOpen]]
 *        .
 * @param leftEndpoint Left endpoint of range. Must not be `null`.
 * @param rightEndpoint Right endpoint of range. May be `null` to
 *        indicate the right endpoint is _Infinity_.
 * @param rightType Must be either [[OSGiVersioRange#RightClosed]] or
 *        [[OSGiVersioRange#RightOpen]].
 * @throws IllegalArgumentException If the arguments are invalid.
 */
  def this(leftType: Char, leftEndpoint: OSGiVersion, rightEndpoint: OSGiVersion, rightType: Char) = {
    this(leftType == OSGiVersionRange.LeftClosed, leftEndpoint, rightEndpoint, rightType == OSGiVersionRange.RightClosed)

    if ((leftType != LeftClosed) && (leftType != LeftOpen))
      throw new IllegalArgumentException("invalid leftType \"" + leftType + "\"")

    if ((rightType != RightOpen) && (rightType != RightClosed))
      throw new IllegalArgumentException("invalid rightType \"" + rightType + "\"")

    if (leftEndpoint == null)
      throw new IllegalArgumentException("null leftEndpoint argument")
  }

  /**
   * The type of the left endpoint of this version range.
   *
   * @return [[OSGiVersioRange#LeftClosed]] if the left endpoint is closed or
   *         [[OSGiVersioRange#LeftOpen]] if the left endpoint is open.
   */
  def getLeftType(): Char = if (leftClosed) LeftClosed else LeftOpen

  /**
   * The type of the right endpoint of this version range.
   *
   * @return [[OSGiVersionRange#RIGHT_CLOSED]] if the right endpoint is closed or
   *         [[OSGiVersionRange#RIGHT_OPEN]] if the right endpoint is open.
   */
  def getRightType(): Char = if (rightClosed) RightClosed else RightOpen

  /**
   * Returns whether this version range includes the specified version.
   *
   * @param version The version to test for inclusion in this version range.
   * @return `true` if the specified version is included in this version
   *         range; `false` otherwise.
   */
  def includes(version: OSGiVersion): Boolean = {
    if (empty) {
      false
    } else if (left.compareTo(version) >= (if (leftClosed) 1 else 0)) {
      false
    } else if (right == null) {
      true
    } else {
      right.compareTo(version) >= (if (rightClosed) 0 else 1)
    }
  }

  /**
   * Returns the intersection of this version range with the specified version
   * ranges.
   *
   * @param ranges The version ranges to intersect with this version range.
   * @return A version range representing the intersection of this version
   *         range and the specified version ranges. If no version ranges are
   *         specified, then this version range is returned.
   */
  def intersection(ranges: OSGiVersionRange*): OSGiVersionRange = {
    if ((ranges == null) || (ranges.isEmpty)) {
      this
    } else {
      // prime with data from this version range
      var closedLeft = leftClosed
      var closedRight = rightClosed
      var endpointLeft = left
      var endpointRight = right

      ranges.foreach { range =>
        var comparison = endpointLeft.compareTo(range.left)
        if (comparison == 0) {
          closedLeft = closedLeft && range.leftClosed
        } else {
          if (comparison < 0) { // move endpointLeft to the right
            endpointLeft = range.left
            closedLeft = range.leftClosed
          }
        }
        if (range.right != null) {
          if (endpointRight == null) {
            endpointRight = range.right
            closedRight = range.rightClosed
          } else {
            comparison = endpointRight.compareTo(range.right)
            if (comparison == 0) {
              closedRight = closedRight && range.rightClosed
            } else {
              if (comparison > 0) { // move endpointRight to the left
                endpointRight = range.right
                closedRight = range.rightClosed
              }
            }
          }
        }
      }
      new OSGiVersionRange(
        if (closedLeft) LeftClosed else LeftOpen,
        endpointLeft,
        endpointRight,
        if (closedRight) RightClosed else RightOpen)
    }
  }

  /**
   * Returns whether this version range contains only a single version.
   *
   * @return `true` if this version range contains only a single version; `false` otherwise.
   */
  def isExact(): Boolean = {
    if (empty || (right == null)) {
      false
    } else if (leftClosed) {
      if (rightClosed) {
        // [l,r]: exact if l == r
        return left.equals(right)
      } else {
        // [l,r): exact if l++ >= r
        val adjacent1 = new OSGiVersion(left.major, left.minor, left.micro, left.qualifier + "-")
        return adjacent1.compareTo(right) >= 0
      }
    } else {
      if (rightClosed) {
        // (l,r] is equivalent to [l++,r]: exact if l++ == r
        val adjacent1 = new OSGiVersion(left.major, left.minor, left.micro, left.qualifier + "-")
        return adjacent1.equals(right)
      } else {
        // (l,r) is equivalent to [l++,r): exact if (l++)++ >=r
        val adjacent2 = new OSGiVersion(left.major, left.minor, left.micro, left.qualifier + "--")
        return adjacent2.compareTo(right) >= 0
      }
    }
  }

  @transient var versionRangeString: String = _

  /**
   * Returns the string representation of this version range.
   *
   * The format of the version range string will be a version string if the
   * right end point is _Infinity_ (`null`) or an interval string.
   *
   * @return The string representation of this version range.
   */
  override def toString(): String = {
    if (versionRangeString == null) {
      val leftVersion = left.toString()
      if (right == null) {
        val result = new StringBuilder(leftVersion.length() + 1)
        result.append(left)
        versionRangeString = result.toString()
      } else {
        val rightVerion = right.toString()
        val result = new StringBuilder(leftVersion.length() + rightVerion.length() + 5)
        result.append(if (leftClosed) LeftClosed else LeftOpen)
        result.append(left)
        result.append(EndpointDelimiter)
        result.append(right)
        result.append(if (rightClosed) RightClosed else RightOpen)
        versionRangeString = result.toString()
      }
    }
    versionRangeString
  }

  @transient var hash: Int = 0

  /** Returns a hash code value for the object. */
  override def hashCode(): Int = {
    if (hash == 0) {
      if (empty) {
        hash = 31
      } else {
        var h = 31 + (if (leftClosed) 7 else 5)
        h = 31 * h + left.hashCode()
        if (right != null) {
          h = 31 * h + right.hashCode()
          h = 31 * h + (if (rightClosed) 7 else 5)
        }
        hash = h
      }
    }
    hash
  }

  /**
   * Compares this [[OSGiVersionRange]] object to another object.
   *
   * A version range is considered to be *equal to* another version
   * range if both the endpoints and their types are equal or if both version
   * ranges are [[#empty empty]].
   *
   * @return `true` if `that` is a `OSGiVersionRange` and is equal to this object; `false` otherwise.
   */
  override def equals(that: Any): Boolean = that match {
    case other: OSGiVersionRange =>
      other.eq(this) || (empty && other.empty) ||
        (if (right == null) {
          (leftClosed == other.leftClosed) && (other.right == null) && left.equals(other.left)
        } else {
          (leftClosed == other.leftClosed) && (rightClosed == other.rightClosed) && left.equals(other.left) && right.equals(other.right)
        })
  }

}
