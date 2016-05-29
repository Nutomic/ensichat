package com.nutomic.ensichat.core.body

import java.nio.ByteBuffer

import com.nutomic.ensichat.core.Address
import com.nutomic.ensichat.core.util.BufferUtils

private[core] object RouteError {

  val Type = 4

  /**
   * Constructs [[RouteError]] instance from byte array.
   */
  def read(array: Array[Byte]): RouteError = {
    val b = ByteBuffer.wrap(array)
    val address = new Address(BufferUtils.getByteArray(b, Address.Length))
    val seqNum = b.getInt
    new RouteError(address, seqNum)
  }

}

private[core] case class RouteError(address: Address, seqNum: Int) extends MessageBody {

  override def protocolType = RouteReply.Type

  override def contentType = -1

  override def write: Array[Byte] = {
    val b = ByteBuffer.allocate(length)
    b.put(address.bytes)
    b.putInt(seqNum)
    b.array()
  }

  override def length = Address.Length + 4

}
