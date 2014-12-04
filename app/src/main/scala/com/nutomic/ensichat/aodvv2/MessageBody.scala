package com.nutomic.ensichat.aodvv2

/**
 * Holds the actual message content.
 */
abstract class MessageBody {

  /**
   * Writes the message contents to a byte array.
   * @return
   */
  def write: Array[Byte]

}
