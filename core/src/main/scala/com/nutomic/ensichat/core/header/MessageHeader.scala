package com.nutomic.ensichat.core.header

import java.nio.ByteBuffer

import com.nutomic.ensichat.core.Address
import com.nutomic.ensichat.core.Message.ReadMessageException
import com.nutomic.ensichat.core.util.BufferUtils

object MessageHeader {

  val Length = AbstractHeader.Length

  /**
   * Constructs header from byte array.
   *
   * @return The header and the message length in bytes.
   */
  @throws(classOf[ReadMessageException])
  def read(bytes: Array[Byte]): (MessageHeader, Int) = {
    val b = ByteBuffer.wrap(bytes, 0, MessageHeader.Length)

    val version = BufferUtils.getUnsignedByte(b)
    if (version != AbstractHeader.Version)
      throw new ReadMessageException("Failed to parse message with unsupported version " + version)
    val protocolType = BufferUtils.getUnsignedByte(b)
    val tokens = BufferUtils.getUnsignedByte(b)
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
 * This is the same as [[AbstractHeader]].
 */
final case class MessageHeader(override val protocolType: Int,
                    override val origin: Address,
                    override val target: Address,
                    override val seqNum: Int,
                    override val tokens: Int,
                    override val hopCount: Int = 0)
  extends AbstractHeader {

  def length: Int = MessageHeader.Length

}
