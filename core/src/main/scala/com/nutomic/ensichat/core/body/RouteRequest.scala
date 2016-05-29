package com.nutomic.ensichat.core.body

import java.nio.ByteBuffer

import com.nutomic.ensichat.core.Address
import com.nutomic.ensichat.core.util.BufferUtils

private[core] object RouteRequest {

  val Type = 2

  /**
   * Constructs [[RouteRequest]] instance from byte array.
   */
  def read(array: Array[Byte]): RouteRequest = {
    val b = ByteBuffer.wrap(array)
    val requested  = new Address(BufferUtils.getByteArray(b, Address.Length))
    val origSeqNum = BufferUtils.getUnsignedShort(b)
    val originMetric = BufferUtils.getUnsignedShort(b)
    val targSeqNum = b.getInt()
    new RouteRequest(requested, origSeqNum, targSeqNum, originMetric)
  }

}

private[core] case class RouteRequest(requested: Address, originSeqNum: Int, targSeqNum: Int, originMetric: Int)
  extends MessageBody {

  override def protocolType = RouteRequest.Type

  override def contentType = -1

  override def write: Array[Byte] = {
    val b = ByteBuffer.allocate(length)
    b.put(requested.bytes)
    BufferUtils.putUnsignedShort(b, originSeqNum)
    BufferUtils.putUnsignedShort(b, originMetric)
    b.putInt(targSeqNum)
    b.array()
  }

  override def length = 8 + Address.Length

}
