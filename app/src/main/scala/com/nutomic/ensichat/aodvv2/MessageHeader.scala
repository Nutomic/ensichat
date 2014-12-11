package com.nutomic.ensichat.aodvv2

import java.nio.ByteBuffer
import java.util.Date

import com.nutomic.ensichat.util.BufferUtils

object MessageHeader {

  val Length = 20 + 2 * Address.Length

  val DefaultHopLimit = 20

  val Version = 0

  class ParseMessageException(detailMessage: String) extends RuntimeException(detailMessage) {
  }

  /**
   * Constructs [[MessageHeader]] from byte array.
   */
  def read(bytes: Array[Byte]): MessageHeader = {
    val b = ByteBuffer.wrap(bytes, 0, Length)

    val versionAndType = BufferUtils.getUnsignedShort(b)
    val version = versionAndType >>> 12
    if (version != Version)
      throw new ParseMessageException("Failed to parse message with unsupported version " + version)
    val messageType = versionAndType & 0xfff
    val hopLimit = BufferUtils.getUnsignedByte(b)
    val hopCount = BufferUtils.getUnsignedByte(b)

    val length = BufferUtils.getUnsignedInt(b)
    val time = new Date(b.getInt().toLong * 1000)
    val origin = new Address(BufferUtils.getByteArray(b, Address.Length))
    val target = new Address(BufferUtils.getByteArray(b, Address.Length))

    val seqNum = BufferUtils.getUnsignedShort(b)
    val metric = BufferUtils.getUnsignedByte(b)

    new MessageHeader(messageType, hopLimit, origin, target, seqNum, metric, time, length, hopCount)
  }

}

/**
 * First part of any message, used for routing.
 */
class MessageHeader(val MessageType: Int,
                    val HopLimit: Int,
                    val Origin: Address,
                    val Target: Address,
                    val SequenceNumber: Int,
                    val Metric: Int,
                    val Time: Date = new Date(),
                    val Length: Long = -1,
                    val HopCount: Int = 0) {

  /**
   * Writes the header to byte array.
   */
  def write(contentLength: Int): Array[Byte] = {
    val b = ByteBuffer.allocate(MessageHeader.Length)

    val versionAndType = (MessageHeader.Version << 12) | MessageType
    BufferUtils.putUnsignedShort(b, versionAndType)
    BufferUtils.putUnsignedByte(b, HopLimit)
    BufferUtils.putUnsignedByte(b, HopCount)

    BufferUtils.putUnsignedInt(b, MessageHeader.Length + contentLength)
    b.putInt((Time.getTime / 1000).toInt)
    b.put(Origin.Bytes)
    b.put(Target.Bytes)

    BufferUtils.putUnsignedShort(b, SequenceNumber)
    BufferUtils.putUnsignedByte(b, Metric)
    BufferUtils.putUnsignedByte(b, 0)

    b.array()
  }

  override def equals(a: Any): Boolean = a match {
    case o: MessageHeader =>
      MessageType == o.MessageType &&
        HopLimit == o.HopLimit &&
        Time.getTime / 1000 == o.Time.getTime / 1000 &&
        Origin == o.Origin &&
        Target == o.Target &&
        SequenceNumber == o.SequenceNumber &&
        Metric == o.Metric &&
        HopCount == o.HopCount
        // Don't compare length as it may be unknown (when header was just created without a body).
    case _ => false
  }

  override def toString = "MessageHeader(Version=" + MessageHeader.Version +
    ", Type=" + MessageType + ", HopLimit=" + HopLimit + ", HopCount=" + HopCount +
    ", Time=" + Time + ", Origin=" + Origin + ", Target=" + Target + ", SeqNum=" +
    ", Metric=" + Metric + ", Length=" + Length + ", HopCount=" + HopCount + ")"

}
