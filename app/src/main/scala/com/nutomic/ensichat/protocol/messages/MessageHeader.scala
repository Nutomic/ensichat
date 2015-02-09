package com.nutomic.ensichat.protocol.messages

import java.nio.ByteBuffer

import com.nutomic.ensichat.protocol.{Address, BufferUtils}

object MessageHeader {

  val Length = 12 + 2 * Address.Length

  val DefaultHopLimit = 20

  val Version = 0
  
  val SeqNumRange = 0 until ((2 << 16) - 1)

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

    val seqNum = BufferUtils.getUnsignedShort(b)

    new MessageHeader(messageType, hopLimit, origin, target, seqNum, length, hopCount)
  }

}

/**
 * First part of any message, used for routing.
 */
case class MessageHeader(messageType: Int,
                    hopLimit: Int,
                    origin: Address,
                    target: Address,
                    seqNum: Int,
                    length: Long = -1,
                    hopCount: Int = 0) {

  /**
   * Writes the header to byte array.
   */
  def write(contentLength: Int): Array[Byte] = {
    val b = ByteBuffer.allocate(MessageHeader.Length)

    val versionAndType = (MessageHeader.Version << 12) | messageType
    BufferUtils.putUnsignedShort(b, versionAndType)
    BufferUtils.putUnsignedByte(b, hopLimit)
    BufferUtils.putUnsignedByte(b, hopCount)

    BufferUtils.putUnsignedInt(b, MessageHeader.Length + contentLength)
    b.put(origin.bytes)
    b.put(target.bytes)

    BufferUtils.putUnsignedShort(b, seqNum)
    BufferUtils.putUnsignedShort(b, 0)

    b.array()
  }

  override def equals(a: Any): Boolean = a match {
    case o: MessageHeader =>
      messageType == o.messageType &&
        hopLimit == o.hopLimit &&
        origin == o.origin &&
        target == o.target &&
        hopCount == o.hopCount
        // Don't compare length as it may be unknown (when header was just created without a body).
    case _ => false
  }

}
