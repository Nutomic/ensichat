package com.nutomic.ensichat.core.body

import java.nio.ByteBuffer

import com.nutomic.ensichat.core.Message
import com.nutomic.ensichat.core.util.BufferUtils

object UserInfo {

  val Type = 7

  /**
   * Constructs [[UserInfo]] instance from byte array.
   */
  def read(array: Array[Byte]): UserInfo = {
    val bb = ByteBuffer.wrap(array)
    new UserInfo(getValue(bb), getValue(bb))
  }

  private def getValue(bb: ByteBuffer): String = {
    val length = BufferUtils.getUnsignedInt(bb).toInt
    val bytes = new Array[Byte](length)
    bb.get(bytes, 0, length)
    new String(bytes, Message.Charset)
  }

}

/**
 * Holds display name and status of the sender.
 */
case class UserInfo(name: String, status: String) extends MessageBody {

  override def protocolType = -1

  override def contentType = UserInfo.Type

  override def write: Array[Byte] = {
    val b = ByteBuffer.allocate(length)
    put(b, name)
    put(b, status)
    b.array()
  }

  def put(b: ByteBuffer, value: String): ByteBuffer = {
    val bytes = value.getBytes(Message.Charset)
    BufferUtils.putUnsignedInt(b, bytes.length)
    b.put(bytes)
  }

  override def length = 8 + name.getBytes(Message.Charset).length +
    status.getBytes(Message.Charset).length

}
