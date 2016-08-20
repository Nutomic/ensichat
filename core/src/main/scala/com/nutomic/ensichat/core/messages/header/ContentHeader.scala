package com.nutomic.ensichat.core.messages.header

import java.nio.ByteBuffer

import com.nutomic.ensichat.core.messages.header
import com.nutomic.ensichat.core.routing.Address
import com.nutomic.ensichat.core.util.BufferUtils
import org.joda.time.DateTime

object ContentHeader {

  val Length = 10

  val ContentMessageType = 255

  val SeqNumRange = 0 until 1 << 16

  /**
   * Constructs [[MessageHeader]] from byte array.
   */
  def read(mh: header.AbstractHeader, bytes: Array[Byte]): (ContentHeader, Array[Byte]) = {
    val b = ByteBuffer.wrap(bytes)

    val contentType = BufferUtils.getUnsignedShort(b)
    val messageId   = BufferUtils.getUnsignedInt(b)
    val time        = BufferUtils.getUnsignedInt(b)

    val ch = new ContentHeader(mh.origin, mh.target, mh.seqNum, contentType, Some(messageId),
      Some(new DateTime(time * 1000)), mh.tokens, mh.hopCount)

    val remaining = new Array[Byte](b.remaining())
    b.get(remaining, 0, b.remaining())
    (ch, remaining)
  }

}

/**
 * Header for user-sent messages.
 *
 * This is [[header.AbstractHeader]] with messageId and time fields set.
 */
final case class ContentHeader(override val origin: Address,
                    override val target: Address,
                    override val seqNum: Int,
                    contentType: Int,
                    override val messageId: Some[Long],
                    override val time: Some[DateTime],
                    override val tokens: Int,
                    override val hopCount: Int = 0)
  extends header.AbstractHeader {

  override val protocolType = ContentHeader.ContentMessageType

  /**
   * Writes the header to byte array.
   */
  override def write(contentLength: Int): Array[Byte] = {
    val b = ByteBuffer.allocate(length)

    b.put(super.write(contentLength))

    BufferUtils.putUnsignedShort(b, contentType)
    BufferUtils.putUnsignedInt(b, messageId.get)
    BufferUtils.putUnsignedInt(b, time.get.getMillis / 1000)

    b.array()
  }

  override def length = header.AbstractHeader.Length + ContentHeader.Length

  override def equals(a: Any): Boolean = a match {
    case o: ContentHeader =>
      super.equals(a) &&
        contentType         == o.contentType &&
        messageId           == o.messageId &&
        time.get.getMillis / 1000 == o.time.get.getMillis / 1000
    case _ => false
  }

}
