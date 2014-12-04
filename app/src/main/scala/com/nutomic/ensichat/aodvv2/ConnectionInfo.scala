package com.nutomic.ensichat.aodvv2

import java.nio.ByteBuffer
import java.security.spec.X509EncodedKeySpec
import java.security.{KeyFactory, PublicKey}

import com.nutomic.ensichat.messages.Crypto
import com.nutomic.ensichat.util.BufferUtils

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
class ConnectionInfo(val key: PublicKey) extends MessageBody {

  override def write: Array[Byte] = {
    val b = ByteBuffer.allocate(4 + key.getEncoded.length)
    BufferUtils.putUnsignedInt(b, key.getEncoded.length)
    b.put(key.getEncoded)
    b.array()
  }

}
