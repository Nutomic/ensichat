package com.nutomic.ensichat.protocol.body

import java.nio.ByteBuffer
import java.util

import com.nutomic.ensichat.util.BufferUtils

object PaymentInformation {

  val Type = 6

  /**
   * Constructs [[PaymentInformation]] instance from byte array.
   */
  def read(array: Array[Byte]): PaymentInformation = {
    val b = ByteBuffer.wrap(array)
    val length = BufferUtils.getUnsignedInt(b).toInt
    val bytes = new Array[Byte](length)
    b.get(bytes, 0, length)
    new PaymentInformation(bytes)
  }

}

/**
 * Contains bitcoin payment information so bitcoins can be sent to the origin of this message.
 *
 * @param bytes Protobuf-formatted Bitcoin payment message.
 */
case class PaymentInformation(bytes: Array[Byte])
  extends MessageBody {

  override def protocolType = -1

  override def contentType = PaymentInformation.Type

  override def write: Array[Byte] = {
    val b = ByteBuffer.allocate(length)
    BufferUtils.putUnsignedInt(b, bytes.length)
    b.put(bytes)
    b.array()
  }

  override def length = 4 + bytes.length

  override def equals(a: Any): Boolean = a match {
    case o: PaymentInformation => util.Arrays.equals(bytes, o.bytes)
    case _ => false
  }

  override def toString = BufferUtils.toString(bytes)

}
