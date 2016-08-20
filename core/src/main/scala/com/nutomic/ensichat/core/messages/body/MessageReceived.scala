package com.nutomic.ensichat.core.messages.body

import java.nio.ByteBuffer

import com.nutomic.ensichat.core.util.BufferUtils

object MessageReceived {

  val Type = 8

  /**
    * Constructs [[Text]] instance from byte array.
    */
  def read(array: Array[Byte]): MessageReceived = {
    val b = ByteBuffer.wrap(array)
    val messageId = BufferUtils.getUnsignedInt(b)
    new MessageReceived(messageId)
  }

}

/**
  * Holds a plain text message.
  */
final case class MessageReceived(messageId: Long) extends MessageBody {

  override def protocolType = -1

  override def contentType = MessageReceived.Type

  override def write: Array[Byte] = {
    val b = ByteBuffer.allocate(length)
    // TODO: This should be putUnsignedLong, but doesn't seem possible in the JVM.
    //       Alternatively, we could use signed ints instead.
    BufferUtils.putUnsignedInt(b, messageId)
    b.array()
  }

  override def length = 4

  override def equals(a: Any): Boolean = a match {
    case o: MessageReceived => messageId == o.messageId
    case _ => false
  }

}
