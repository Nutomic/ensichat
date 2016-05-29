package com.nutomic.ensichat.core.body

import java.nio.ByteBuffer

import com.nutomic.ensichat.core.util.BufferUtils

private[core] object RouteReply {

  val Type = 3

  /**
   * Constructs [[RouteReply]] instance from byte array.
   */
  def read(array: Array[Byte]): RouteReply = {
    val b = ByteBuffer.wrap(array)
    val targSeqNum = BufferUtils.getUnsignedShort(b)
    val targMetric = BufferUtils.getUnsignedShort(b)
    new RouteReply(targSeqNum, targMetric)
  }

}

/**
  * Sends information about a route.
  *
  * Note that the fields are named different than described in AODVv2. There, targSeqNum and
  * targMetric are used to describe the seqNum and metric of the node sending the route reply. In
  * Ensichat, we use originSeqNum and originMetric instead, to stay consistent with the header
  * fields. That means header.origin, originSeqNum and originMetric all refer to the node sending
  * this message.
  *
  * @param originSeqNum The current sequence number of the node sending this message.
  * @param originMetric The metric of the current route to the sending node.
  */
private[core] case class RouteReply(originSeqNum: Int, originMetric: Int) extends MessageBody {

  override def protocolType = RouteReply.Type

  override def contentType = -1

  override def write: Array[Byte] = {
    val b = ByteBuffer.allocate(length)
    BufferUtils.putUnsignedShort(b, originSeqNum)
    BufferUtils.putUnsignedShort(b, originMetric)
    b.array()
  }

  override def length = 4

}
