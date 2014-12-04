package com.nutomic.ensichat.aodvv2

import java.nio.ByteBuffer

import com.nutomic.ensichat.util.BufferUtils

object Data {

  val Type = 255

  /**
   * Constructs [[Data]] object from byte array.
   */
  def read(array: Array[Byte]): Data = {
    val b = ByteBuffer.wrap(array)
    val length = BufferUtils.getUnsignedInt(b).toInt
    val data = new Array[Byte](length)
    b.get(data, 0, length)
    new Data(data)
  }

}

/**
 * Container for [[com.nutomic.ensichat.messages.Message]] objects.
 */
@Deprecated
class Data(val data: Array[Byte]) extends MessageBody {

  override def write: Array[Byte] = {
    val b = ByteBuffer.allocate(4 + data.length)
    BufferUtils.putUnsignedInt(b, data.length)
    b.put(data)
    b.array()
  }

}
