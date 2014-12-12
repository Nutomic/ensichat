package com.nutomic.ensichat.protocol.messages

import java.nio.ByteBuffer
import java.util.Date

import com.nutomic.ensichat.protocol.{Address, BufferUtils}

object MessageHeader {

  val Length = 12 + 2 * Address.Length

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
    val origin = new Address(BufferUtils.getByteArray(b, Address.Length))
    val target = new Address(BufferUtils.getByteArray(b, Address.Length))

    new MessageHeader(messageType, hopLimit, origin, target, length, hopCount)
  }

}

/**
 * First part of any message, used for routing.
 */
case class MessageHeader(MessageType: Int,
                    HopLimit: Int,
                    Origin: Address,
                    Target: Address,
                    Length: Long = -1,
                    HopCount: Int = 0) {

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
    b.put(Origin.Bytes)
    b.put(Target.Bytes)

    BufferUtils.putUnsignedInt(b, 0)

    b.array()
  }

  override def equals(a: Any): Boolean = a match {
    case o: MessageHeader =>
      MessageType == o.MessageType &&
        HopLimit == o.HopLimit &&
        Origin == o.Origin &&
        Target == o.Target &&
        HopCount == o.HopCount
        // Don't compare length as it may be unknown (when header was just created without a body).
    case _ => false
  }

}
