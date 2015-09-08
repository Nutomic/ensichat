package com.nutomic.ensichat.core.util

import java.nio.ByteBuffer

/**
 * Provides various helper methods for [[ByteBuffer]].
 */
object BufferUtils {

  def getUnsignedByte(bb: ByteBuffer): Short       = (bb.get & 0xff).toShort

  def putUnsignedByte(bb: ByteBuffer, value: Int)  = bb.put((value & 0xff).toByte)

  def getUnsignedShort(bb: ByteBuffer): Int        = bb.getShort & 0xffff

  def putUnsignedShort(bb: ByteBuffer, value: Int) = bb.putShort((value & 0xffff).toShort)

  def getUnsignedInt(bb: ByteBuffer): Long         = bb.getInt.toLong & 0xffffffffL

  def putUnsignedInt(bb: ByteBuffer, value: Long)  = bb.putInt((value & 0xffffffffL).toInt)

  /**
   * Reads a byte array with the given length and returns it.
   */
  def getByteArray(bb: ByteBuffer, numBytes: Int): Array[Byte] = {
    val b = new Array[Byte](numBytes)
    bb.get(b, 0, numBytes)
    b
  }

  def toString(array: Array[Byte]) = array.map("%02X".format(_)).mkString

}
