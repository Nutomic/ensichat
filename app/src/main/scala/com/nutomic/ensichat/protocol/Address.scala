package com.nutomic.ensichat.protocol

import java.util

object Address {

  val Length = 32

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
case class Address(bytes: Array[Byte]) {

  require(bytes.length == Address.Length, "Invalid address length (was " + bytes.length + ")")

  def this(hex: String) {
    this(hex.sliding(2, 2).map(Integer.parseInt(_, 16).toByte).toArray)
  }

  override def hashCode = util.Arrays.hashCode(bytes)

  override def equals(a: Any) = a match {
    case o: Address => bytes.deep == o.bytes.deep
    case _ => false
  }

  override def toString = bytes.map("%02X".format(_)).mkString

}
