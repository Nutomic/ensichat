package com.nutomic.ensichat.core.messages.header

import java.nio.ByteBuffer

import com.nutomic.ensichat.core.routing.Address
import com.nutomic.ensichat.core.util.BufferUtils
import org.joda.time.DateTime

object AbstractHeader {

  val InitialForwardingTokens = 3

  val MaxForwardingTokens = 6

  val Version = 0

  private[header] val Length = 10 + 2 * Address.Length

}

/**
 * Contains the header fields and functionality that are used both in [[MessageHeader]] and
 * [[ContentHeader]].
 *
 * The fields messageId and time are only set in [[ContentHeader]].
 */
trait AbstractHeader {

  def protocolType: Int
  def tokens: Int
  def hopCount: Int
  def origin: Address
  def target: Address
  def seqNum: Int
  def messageId: Option[Long] = None
  def time: Option[DateTime] = None

  /**
   * Writes the header to byte array.
   */
  def write(contentLength: Int): Array[Byte] = {
    val b = ByteBuffer.allocate(AbstractHeader.Length)

    BufferUtils.putUnsignedByte(b, AbstractHeader.Version)
    BufferUtils.putUnsignedByte(b, protocolType)
    BufferUtils.putUnsignedByte(b, tokens)
    BufferUtils.putUnsignedByte(b, hopCount)

    BufferUtils.putUnsignedInt(b, length + contentLength)
    b.put(origin.bytes)
    b.put(target.bytes)

    BufferUtils.putUnsignedShort(b, seqNum)

    b.array()
  }

  /**
   * Returns true if this object is an instance of [[ContentHeader]].
   */
  def isContentMessage = protocolType == ContentHeader.ContentMessageType

  def length: Int

  override def equals(a: Any): Boolean = a match {
    case o: AbstractHeader =>
      protocolType  == o.protocolType &&
        tokens      == o.tokens &&
        hopCount    == o.hopCount &&
        origin      == o.origin &&
        target      == o.target &&
        seqNum      == o.seqNum
    case _ => false
  }

}
