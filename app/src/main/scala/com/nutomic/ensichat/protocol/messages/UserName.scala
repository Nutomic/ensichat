package com.nutomic.ensichat.protocol.messages

import java.nio.ByteBuffer

import com.nutomic.ensichat.protocol.BufferUtils

object UserName {

  val Type = 7

  /**
   * Constructs [[UserName]] instance from byte array.
   */
  def read(array: Array[Byte]): UserName = {
    val b = ByteBuffer.wrap(array)
    val length = BufferUtils.getUnsignedInt(b).toInt
    val bytes = new Array[Byte](length)
    b.get(bytes, 0, length)
    new UserName(new String(bytes, Message.Charset))
  }

}

/**
 * Holds the display name of the sender.
 */
case class UserName(Name: String) extends MessageBody {

  override def messageType = UserName.Type

  override def write: Array[Byte] = {
    val b = ByteBuffer.allocate(length)
    val bytes = Name.getBytes(Message.Charset)
    BufferUtils.putUnsignedInt(b, bytes.length)
    b.put(bytes)
    b.array()
  }

  override def length = 4 + Name.getBytes(Message.Charset).length

}
