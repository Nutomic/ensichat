package com.nutomic.ensichat.core.body

import java.nio.ByteBuffer
import java.util

import com.nutomic.ensichat.core.util.BufferUtils

object CryptoData {

  /**
   * Constructs [[CryptoData]] instance from byte array.
   */
  def read(array: Array[Byte]): (CryptoData, Array[Byte]) = {
    val b = ByteBuffer.wrap(array)
    val signatureLength = BufferUtils.getUnsignedShort(b)
    val keyLength = BufferUtils.getUnsignedShort(b)
    val signature = new Array[Byte](signatureLength)
    b.get(signature, 0, signatureLength)

    val key =
      if (keyLength != 0) {
        val key = new Array[Byte](keyLength)
        b.get(key, 0, keyLength)
        Option(key)
      }
      else None

    val remaining = new Array[Byte](b.remaining())
    b.get(remaining, 0, b.remaining())
    (new CryptoData(Option(signature), key), remaining)
  }

}

/**
 * Holds the signature and (optional) key that are stored in a message.
 */
case class CryptoData(signature: Option[Array[Byte]], key: Option[Array[Byte]]) {

  override def equals(a: Any): Boolean = a match {
    case o: CryptoData => util.Arrays.equals(signature.orNull, o.signature.orNull) &&
      util.Arrays.equals(key.orNull, o.key.orNull)
    case _ => false
  }

  /**
   * Writes this object into a new byte array.
   */
  def write: Array[Byte] = {
    val b = ByteBuffer.allocate(length)
    BufferUtils.putUnsignedShort(b, signature.get.length)
    BufferUtils.putUnsignedShort(b, keyLength)
    b.put(signature.get)
    if (key.nonEmpty) b.put(key.get)
    b.array()
  }

  def length = 4 + signature.get.length + keyLength

  private def keyLength = if (key.isDefined) key.get.length else 0

}
