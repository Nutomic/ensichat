package com.nutomic.ensichat.core.messages.header

import java.nio.ByteBuffer

import com.nutomic.ensichat.core.messages.Message.ReadMessageException
import com.nutomic.ensichat.core.messages.{Message, header}
import com.nutomic.ensichat.core.routing.Address
import com.nutomic.ensichat.core.util.BufferUtils

object MessageHeader {

  val Length = header.AbstractHeader.Length

  /**
   * Constructs header from byte array.
   *
   * @return The header and the message length in bytes.
   */
  @throws(classOf[ReadMessageException])
  def read(bytes: Array[Byte]): (MessageHeader, Int) = {
    val b = ByteBuffer.wrap(bytes, 0, MessageHeader.Length)

    val version = BufferUtils.getUnsignedByte(b)
    if (version != header.AbstractHeader.Version)
      throw new ReadMessageException("Failed to parse message with unsupported version " + version)
    val protocolType = BufferUtils.getUnsignedByte(b)
    val tokens = BufferUtils.getUnsignedByte(b)
    if (tokens > header.AbstractHeader.MaxForwardingTokens)
      throw new ReadMessageException(s"Received message with too many forwarding tokens ($tokens tokens)")
    val hopCount = BufferUtils.getUnsignedByte(b)

    val length = BufferUtils.getUnsignedInt(b)
    if (length < Length)
      throw new ReadMessageException("Received message with invalid length " + length)
    val origin = new Address(BufferUtils.getByteArray(b, Address.Length))
    val target = new Address(BufferUtils.getByteArray(b, Address.Length))

    val seqNum = BufferUtils.getUnsignedShort(b)

    (new MessageHeader(protocolType, origin, target, seqNum, tokens, hopCount), length.toInt)
  }

}

/**
 * First part of any message, used for routing.
 *
 * This is the same as [[header.AbstractHeader]].
 */
final case class MessageHeader(override val protocolType: Int,
                    override val origin: Address,
                    override val target: Address,
                    override val seqNum: Int,
                    override val tokens: Int,
                    override val hopCount: Int = 0)
  extends header.AbstractHeader {

  def length: Int = MessageHeader.Length

}
