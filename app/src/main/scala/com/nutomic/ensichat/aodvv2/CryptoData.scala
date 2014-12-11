package com.nutomic.ensichat.aodvv2

import java.nio.ByteBuffer
import java.util.Arrays

import com.nutomic.ensichat.util.BufferUtils

object CryptoData {

  /**
   * Constructs [[CryptoData]] instance from byte array.
   */
  def read(array: Array[Byte]): (CryptoData, Array[Byte]) = {
    val b = ByteBuffer.wrap(array)
    val signatureLength = BufferUtils.getUnsignedInt(b).toInt
    val signature = new Array[Byte](signatureLength)
    b.get(signature, 0, signatureLength)

    val keyLength = BufferUtils.getUnsignedInt(b).toInt
    val key =
      if (keyLength != 0) {
        val key = new Array[Byte](keyLength)
        b.get(key, 0, keyLength)
        Some(key)
      }
      else None

    val remaining = new Array[Byte](b.remaining())
    b.get(remaining, 0, b.remaining())
    (new CryptoData(Some(signature), key), remaining)

  }

}

/**
 * Holds the signature and (optional) key that are stored in a message.
 */
class CryptoData(val Signature: Option[Array[Byte]], val Key: Option[Array[Byte]]) {

  override def equals(a: Any): Boolean = a match {
    case o: CryptoData =>
      Arrays.equals(Signature.orNull, o.Signature.orNull) && Arrays.equals(Key.orNull, o.Key.orNull)
    case _ => false
  }

  /**
   * Writes this object into a new byte array.
   * @return
   */
  def write: Array[Byte] = {
    val b = ByteBuffer.allocate(length)
    BufferUtils.putUnsignedInt(b, Signature.get.length)
    b.put(Signature.get)
    BufferUtils.putUnsignedInt(b, keyLength)
    if (Key.nonEmpty) b.put(Key.get)
    b.array()
  }

  def length = 8 + Signature.get.length + keyLength

  private def keyLength = if (Key.isDefined) Key.get.length else 0

  override def toString = "CryptoData(Signature.length=" + Signature.foreach(_.length) +
    ", Key.length=" + Key.foreach(_.length) + ")"

}