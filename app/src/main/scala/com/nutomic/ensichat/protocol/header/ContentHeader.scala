package com.nutomic.ensichat.protocol.header

import java.nio.ByteBuffer
import java.util.Date

import com.nutomic.ensichat.protocol.Address
import com.nutomic.ensichat.util.BufferUtils

object ContentHeader {

  val Length = 10

  val ContentMessageType = 255

  val SeqNumRange = 0 until 1 << 16

  /**
   * Constructs [[MessageHeader]] from byte array.
   */
  def read(mh: AbstractHeader, bytes: Array[Byte]): (ContentHeader, Array[Byte]) = {
    val b = ByteBuffer.wrap(bytes)

    val contentType = BufferUtils.getUnsignedShort(b)
    val messageId   = BufferUtils.getUnsignedInt(b)
    val time        = BufferUtils.getUnsignedInt(b)

    val ch = new ContentHeader(mh.origin, mh.target, mh.seqNum, contentType, Some(messageId),
      Some(new Date(time * 1000)), false, mh.hopCount)

    val remaining = new Array[Byte](b.remaining())
    b.get(remaining, 0, b.remaining())
    (ch, remaining)
  }

}

/**
 * Header for user-sent messages.
 *
 * This is [[AbstractHeader]] with messageId and time fields set.
 *
 * @param read Specifies if the message was read by the local user. Never transmitted.
 */
case class ContentHeader(override val origin: Address,
                    override val target: Address,
                    override val seqNum: Int,
                    contentType: Int,
                    override val messageId: Some[Long],
                    override val time: Some[Date],
                    read: Boolean,
                    override val hopCount: Int = 0,
                    override val hopLimit: Int = AbstractHeader.DefaultHopLimit)
  extends AbstractHeader {

  override val protocolType = ContentHeader.ContentMessageType

  /**
   * Writes the header to byte array.
   */
  override def write(contentLength: Int): Array[Byte] = {
    val b = ByteBuffer.allocate(length)

    b.put(super.write(contentLength))

    BufferUtils.putUnsignedShort(b, contentType)
    BufferUtils.putUnsignedInt(b, messageId.get)
    BufferUtils.putUnsignedInt(b, time.get.getTime / 1000)

    b.array()
  }

  override def length = AbstractHeader.Length + ContentHeader.Length

  override def equals(a: Any): Boolean = a match {
    case o: ContentHeader =>
      super.equals(a) &&
        contentType         == o.contentType &&
        messageId           == o.messageId &&
        time.get.getTime / 1000 == o.time.get.getTime / 1000 &&
        read                == o.read
    case _ => false
  }

}
