package com.nutomic.ensichat

object Message {

  /**
   * Constructs a new message from transferred bytes.
   */
  def fromByteArray(data: Array[Byte]): Message = {
    return new Message(new String(data))
  }

}

/**
 * Base class for all messages that can be passed between bluetooth devices.
 *
 * Provides methods for (de-)serialization.
 */
class Message(text: String) {

  /**
   * Converts message to bytes for transfer.
   * @return
   */
  def toByteArray(): Array[Byte] = {
    return text.getBytes()
  }

  override def toString = text

}
