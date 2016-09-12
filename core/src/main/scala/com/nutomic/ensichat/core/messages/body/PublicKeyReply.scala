package com.nutomic.ensichat.core.messages.body

import java.nio.ByteBuffer
import java.security.spec.X509EncodedKeySpec
import java.security.{KeyFactory, PublicKey}

import com.nutomic.ensichat.core.util.BufferUtils
import com.nutomic.ensichat.core.util.Crypto

object PublicKeyReply {

  val Type = 6

  /**
    * Constructs [[ConnectionInfo]] instance from byte array.
    */
  def read(array: Array[Byte]): PublicKeyReply = {
    val b = ByteBuffer.wrap(array)
    val length = BufferUtils.getUnsignedInt(b).toInt
    val encoded = new Array[Byte](length)
    b.get(encoded, 0, length)

    val factory = KeyFactory.getInstance(Crypto.PublicKeyAlgorithm)
    val key = factory.generatePublic(new X509EncodedKeySpec(encoded))
    new PublicKeyReply(key)
  }

}

case class PublicKeyReply(key: PublicKey) extends MessageBody {

  override def protocolType = PublicKeyRequest.Type

  override def contentType = -1

  override def write: Array[Byte] = {
    val b = ByteBuffer.allocate(length)
    BufferUtils.putUnsignedInt(b, key.getEncoded.length)
    b.put(key.getEncoded)
    b.array()
  }

  override def length = 4 + key.getEncoded.length

}
