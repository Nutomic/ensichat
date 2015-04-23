package com.nutomic.ensichat.protocol.body

import java.nio.ByteBuffer

import com.nutomic.ensichat.protocol.Message
import com.nutomic.ensichat.util.BufferUtils

object Text {

  val Type = 3

  /**
   * Constructs [[Text]] instance from byte array.
   */
  def read(array: Array[Byte]): Text = {
    val b = ByteBuffer.wrap(array)
    val length = BufferUtils.getUnsignedInt(b).toInt
    val bytes = new Array[Byte](length)
    b.get(bytes, 0, length)
    new Text(new String(bytes, Message.Charset))
  }

}

/**
 * Holds a plain text message.
 */
case class Text(text: String) extends MessageBody {

  override def protocolType = -1

  override def contentType = Text.Type

  override def write: Array[Byte] = {
    val b = ByteBuffer.allocate(length)
    val bytes = text.getBytes(Message.Charset)
    BufferUtils.putUnsignedInt(b, bytes.length)
    b.put(bytes)
    b.array()
  }

  override def length = 4 + text.getBytes(Message.Charset).length

  override def equals(a: Any): Boolean = a match {
    case o: Text => text == text
    case _ => false
  }

}
