package com.nutomic.ensichat.protocol.messages

import java.nio.ByteBuffer
import java.util.Arrays

import android.util.Log
import com.nutomic.ensichat.protocol.BufferUtils

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
case class CryptoData(Signature: Option[Array[Byte]], Key: Option[Array[Byte]]) {

  override def equals(a: Any): Boolean = a match {
    case o: CryptoData =>
      Arrays.equals(Signature.orNull, o.Signature.orNull) && Arrays.equals(Key.orNull, o.Key.orNull)
    case _ => false
  }

  /**
   * Writes this object into a new byte array.
   */
  def write: Array[Byte] = {
    val b = ByteBuffer.allocate(length)
    BufferUtils.putUnsignedShort(b, Signature.get.length)
    BufferUtils.putUnsignedShort(b, keyLength)
    b.put(Signature.get)
    if (Key.nonEmpty) b.put(Key.get)
    b.array()
  }

  def length = 4 + Signature.get.length + keyLength

  private def keyLength = if (Key.isDefined) Key.get.length else 0

}