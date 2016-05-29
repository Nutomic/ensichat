package com.nutomic.ensichat.core

object Address {

  val Length = 32

  /**
   * Number of characters between each pair of dashes in [[Address.toString]].
   */
  val GroupLength = 8

  // 32 bytes, all ones
  // 0xffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff
  val Broadcast = new Address("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF")

  // 32 bytes, all zeros
  // 0x0000000000000000000000000000000000000000000000000000000000000000
  val Null = new Address("0000000000000000000000000000000000000000000000000000000000000000")

}

/**
 * Holds a device address and provides conversion methods.
 *
 * @param bytes SHA-256 hash of the node's public key.
 */
final case class Address(bytes: Array[Byte]) {

  require(bytes.length == Address.Length, "Invalid address length (was " + bytes.length + ")")

  /**
   * Parses address from string. Dash characters ("-") are ignored.
   */
  def this(hex: String) {
    this(hex
      .replace("-", "")
      .sliding(2, 2)
      .map(Integer.parseInt(_, 16).toByte)
      .toArray)
  }

  override def hashCode = java.util.Arrays.hashCode(bytes)

  override def equals(a: Any) = a match {
    case o: Address => bytes.deep == o.bytes.deep
    case _ => false
  }

  /**
   * Converts address to a string, with groups seperated by dashes.
   */
  override def toString =
    bytes
      .map("%02X".format(_))
      .mkString
      .grouped(Address.GroupLength)
      .reduce(_ + "-" + _)

  /**
    * Returns shortened address, useful for debugging.
    */
  def short = toString.split("-").head

}
