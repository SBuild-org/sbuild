package de.tototec.sbuild.internal

import java.util.StringTokenizer

object OSGiVersion {

  private val SEPARATOR = "."

  /**
   * The empty version "0.0.0". Equivalent to calling `new OSGiVersion(0,0,0)`.
   */
  val emptyVersion = new OSGiVersion(0, 0, 0)

  /**
   * Parses a version identifier from the specified string.
   *
   * See [[OSGiVersion(String)]] for the format of the version string.
   *
   * @param version
   *            String representation of the version identifier. Leading and
   *            trailing whitespace will be ignored.
   * @return A `OSGiVersion` object representing the version
   *         identifier. If `version` is `null` or the
   *         empty string then `emptyVersion` will be returned.
   * @throws IllegalArgumentException
   *             If `version` is improperly formatted.
   */
  def parseVersion(version: String): OSGiVersion = version match {
    case null => emptyVersion
    case v if v.trim.length == 0 => emptyVersion
    case v => new OSGiVersion(v.trim)
  }
}

/**
 * Version identifier for bundles and packages.
 *
 * Version identifiers have four components.
 *  - Major version. A non-negative integer.
 *  - Minor version. A non-negative integer.
 *  - Micro version. A non-negative integer.
 *  - Qualifier. A text string. See <code>Version(String)</code> for the format of the qualifier string.
 *
 * `OSGiVersion` objects are immutable.
 *
 */
class OSGiVersion() extends Comparable[OSGiVersion] {

  private var _major: Int = 0
  private var _minor: Int = 0
  private var _micro: Int = 0
  private var _qualifier: String = null

  def major = _major
  def minor = _minor
  def micro = _micro
  def qualifier = _qualifier

  /**
   * Creates a version identifier from the specified components.
   *
   * @param major Major component of the version identifier.
   * @param minor Minor component of the version identifier.
   * @param micro Micro component of the version identifier.
   * @param qualifier
   *   Qualifier component of the version identifier.
   *   If `null` is specified, then the qualifier will be set to the empty string.
   * @throws IllegalArgumentException
   *   If the numerical components are negative or the qualifier string is invalid.
   */
  def this(major: Int, minor: Int, micro: Int, qualifier: String) {
    this
    this._major = major
    this._minor = minor
    this._micro = micro
    this._qualifier = if (qualifier == null) "" else qualifier
    validate
  }

  /**
   * Creates a version identifier from the specified numerical components.
   *
   * The qualifier is set to the empty string.
   *
   * @param major Major component of the version identifier.
   * @param minor Minor component of the version identifier.
   * @param micro Micro component of the version identifier.
   * @throws IllegalArgumentException If the numerical components are negative.
   */
  def this(major: Int, minor: Int, micro: Int) {
    this(major, minor, micro, null)
  }

  /**
   * Created a version identifier from the specified string.
   *
   * Here is the grammar for version strings.
   *
   * {{{
   * version ::= major('.'minor('.'micro('.'qualifier)?)?)?
   * major ::= digit+
   * minor ::= digit+
   * micro ::= digit+
   * qualifier ::= (alpha|digit|'_'|'-')+
   * digit ::= [0..9]
   * alpha ::= [a..zA..Z]
   * }}}
   *
   * There must be no whitespace in version.
   *
   * @param version String representation of the version identifier.
   * @throws IllegalArgumentException If `version` is improperly formatted.
   */
  def this(version: String) {
    this
    var major = 0
    var minor = 0
    var micro = 0
    var qualifier = ""
    val st = new StringTokenizer(version, OSGiVersion.SEPARATOR, true)
    major = Integer.parseInt(st.nextToken())
    if (st.hasMoreTokens()) {
      st.nextToken()
      minor = Integer.parseInt(st.nextToken())
      if (st.hasMoreTokens()) {
        st.nextToken()
        micro = Integer.parseInt(st.nextToken())
        if (st.hasMoreTokens()) {
          st.nextToken()
          qualifier = st.nextToken()
          if (st.hasMoreTokens()) {
            throw new IllegalArgumentException("invalid format")
          }
        }
      }
    }
    this._major = major
    this._minor = minor
    this._micro = micro
    this._qualifier = qualifier
    validate()
  }

  /**
   * Called by the OSGiVersion constructors to validate the version components.
   *
   * @throws IllegalArgumentException
   *   If the numerical components are negative or the qualifier string is invalid.
   */
  private def validate() {
    if (major < 0) throw new IllegalArgumentException("negative major")
    if (minor < 0) throw new IllegalArgumentException("negative minor")
    if (micro < 0) throw new IllegalArgumentException("negative micro")

    val length = qualifier.length
    for (
      i <- 0 until length if "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789_-"
        .indexOf(qualifier.charAt(i)) ==
        -1
    ) {
      throw new IllegalArgumentException("invalid qualifier")
    }
  }

  /**
   * Returns the string representation of this version identifier.
   *
   * The format of the version string will be `major.minor.micro`
   * if qualifier is the empty string or
   * `major.minor.micro.qualifier` otherwise.
   *
   * @return The string representation of this version identifier.
   */
  override def toString(): String = {
    val base = major + OSGiVersion.SEPARATOR + minor + OSGiVersion.SEPARATOR + micro
    if (qualifier.length == 0) {
      base
    } else {
      base + OSGiVersion.SEPARATOR + qualifier
    }
  }

  /**
   * Returns a hash code value for the object.
   *
   * @return An integer which is a hash code value for this object.
   */
  override def hashCode(): Int = {
    (major << 24) + (minor << 16) + (micro << 8) + qualifier.hashCode
  }

  /**
   * Compares this `OSGiVersion` object to another object.
   *
   * A version is considered to be '''equal to''' another version if the
   * major, minor and micro components are equal and the qualifier component
   * is equal (using `String.equals`).
   *
   * @param object
   *            The `OSGiVersion` object to be compared.
   * @return `true` if `object` is a `Version` and is equal to this object;
   *         `false` otherwise.
   */
  override def equals(that: Any): Boolean = that match {
    case other: OSGiVersion =>
      other.eq(this) ||
        (major == other.major) && (minor == other.minor) && (micro == other.micro) &&
        qualifier == other.qualifier
  }

  /**
   * Compares this `OSGiVersion` object to another object.
   *
   * A version is considered to be '''less than''' another version if its
   * major component is less than the other version's major component, or the
   * major components are equal and its minor component is less than the other
   * version's minor component, or the major and minor components are equal
   * and its micro component is less than the other version's micro component,
   * or the major, minor and micro components are equal and it's qualifier
   * component is less than the other version's qualifier component (using
   * [[String#compareTo]]).
   *
   * A version is considered to be '''equal to''' another version if the
   * major, minor and micro components are equal and the qualifier component
   * is equal (using [[String#compareTo]]).
   *
   * @param object The `OSGiVersion` object to be compared.
   * @return A negative integer, zero, or a positive integer if this object is
   *         less than, equal to, or greater than the specified
   *         `OSGiVersion` object.
   * @throws ClassCastException If the specified object is not a `OSGiVersion`.
   */
  def compareTo(other: OSGiVersion): Int = {
    if (other == this) {
      return 0
    }
    var result = major - other.major
    if (result != 0) {
      return result
    }
    result = minor - other.minor
    if (result != 0) {
      return result
    }
    result = micro - other.micro
    if (result != 0) {
      return result
    }
    qualifier.compareTo(other.qualifier)
  }
}
