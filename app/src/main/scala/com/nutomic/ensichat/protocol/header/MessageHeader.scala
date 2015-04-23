package com.nutomic.ensichat.protocol.header

import java.nio.ByteBuffer

import com.nutomic.ensichat.protocol.Message.ParseMessageException
import com.nutomic.ensichat.protocol.{Address, Message}
import com.nutomic.ensichat.util.BufferUtils

object MessageHeader {

  val Length = AbstractHeader.Length

  /**
   * Constructs header from byte array.
   *
   * @return The header and the message length in bytes.
   */
  def read(bytes: Array[Byte]): (MessageHeader, Int) = {
    val b = ByteBuffer.wrap(bytes, 0, MessageHeader.Length)

    val version = BufferUtils.getUnsignedByte(b)
    if (version != AbstractHeader.Version)
      throw new ParseMessageException("Failed to parse message with unsupported version " + version)
    val protocolType = BufferUtils.getUnsignedByte(b)
    val hopLimit = BufferUtils.getUnsignedByte(b)
    val hopCount = BufferUtils.getUnsignedByte(b)

    val length = BufferUtils.getUnsignedInt(b)
    val origin = new Address(BufferUtils.getByteArray(b, Address.Length))
    val target = new Address(BufferUtils.getByteArray(b, Address.Length))

    val seqNum = BufferUtils.getUnsignedShort(b)

    (new MessageHeader(protocolType, origin, target, seqNum, hopCount, hopLimit), length.toInt)
  }

}

/**
 * First part of any message, used for routing.
 *
 * This is the same as [[AbstractHeader]].
 */
case class MessageHeader(override val protocolType: Int,
                    override val origin: Address,
                    override val target: Address,
                    override val seqNum: Int,
                    override val hopCount: Int = 0,
                    override val hopLimit: Int = AbstractHeader.DefaultHopLimit)
  extends AbstractHeader {

  def length: Int = MessageHeader.Length

}
