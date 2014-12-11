package com.nutomic.ensichat.protocol.messages

import java.nio.ByteBuffer

import com.nutomic.ensichat.protocol.BufferUtils

object Text {

  val Type = 6

  val Charset = "UTF-8"

  /**
   * Constructs [[Text]] instance from byte array.
   */
  def read(array: Array[Byte]): Text = {
    val b = ByteBuffer.wrap(array)
    val length = BufferUtils.getUnsignedInt(b).toInt
    val bytes = new Array[Byte](length)
    b.get(bytes, 0, length)
    new Text(new String(bytes, Text.Charset))
  }

}

/**
 * Holds a plain text message.
 */
class Text(val text: String) extends MessageBody {

  override def Type = Text.Type

  override def write: Array[Byte] = {
    val bytes = text.getBytes(Text.Charset)
    val b = ByteBuffer.allocate(4 + bytes.length)
    BufferUtils.putUnsignedInt(b, bytes.length)
    b.put(bytes)
    b.array()
  }

  override def equals(a: Any): Boolean = a match {
    case o: Text => text == o.text
    case _ => false
  }

  override def toString = "Text(" + text + ")"

  override def length = write.length

}
