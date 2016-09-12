package com.nutomic.ensichat.core.messages.body

import java.nio.ByteBuffer

import com.nutomic.ensichat.core.routing.Address
import com.nutomic.ensichat.core.util.BufferUtils

object PublicKeyRequest {

  val Type = 5

  /**
    * Constructs [[Text]] instance from byte array.
    */
  def read(array: Array[Byte]): PublicKeyRequest = {
    val b = ByteBuffer.wrap(array)
    val length = BufferUtils.getUnsignedInt(b).toInt
    val bytes = new Array[Byte](length)
    b.get(bytes, 0, length)
    new PublicKeyRequest(new Address(bytes))
  }

}

case class PublicKeyRequest(address: Address) extends MessageBody {

  require(address != Address.Broadcast, "")
  require(address != Address.Null, "")

  override def protocolType = PublicKeyRequest.Type

  override def contentType = -1

  override def write: Array[Byte] = {
    val b = ByteBuffer.allocate(length)
    val bytes = address.bytes
    BufferUtils.putUnsignedInt(b, bytes.length)
    b.put(bytes)
    b.array()
  }


  override def equals(a: Any): Boolean = a match {
    case o: PublicKeyRequest => address == o.address
    case _ => false
  }

  override def length = 4 + address.bytes.length

}
