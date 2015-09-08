package com.nutomic.ensichat.core.body

import java.nio.ByteBuffer
import java.security.spec.X509EncodedKeySpec
import java.security.{KeyFactory, PublicKey}

import com.nutomic.ensichat.core.Crypto
import com.nutomic.ensichat.core.util.BufferUtils

object ConnectionInfo {

  val Type = 0

  val HopLimit = 1

  /**
   * Constructs [[ConnectionInfo]] instance from byte array.
   */
  def read(array: Array[Byte]): ConnectionInfo = {
    val b = ByteBuffer.wrap(array)
    val length = BufferUtils.getUnsignedInt(b).toInt
    val encoded = new Array[Byte](length)
    b.get(encoded, 0, length)

    val factory = KeyFactory.getInstance(Crypto.KeyAlgorithm)
    val key = factory.generatePublic(new X509EncodedKeySpec(encoded))
    new ConnectionInfo(key)
  }

}

/**
 * Holds a node's public key.
 */
case class ConnectionInfo(key: PublicKey) extends MessageBody {

  override def protocolType = ConnectionInfo.Type

  override def contentType = -1

  override def write: Array[Byte] = {
    val b = ByteBuffer.allocate(length)
    BufferUtils.putUnsignedInt(b, key.getEncoded.length)
    b.put(key.getEncoded)
    b.array()
  }

  override def length = 4 + key.getEncoded.length

}
