package com.nutomic.ensichat.protocol.messages

import java.nio.ByteBuffer

object RequestAddContact {

  val Type = 4

  /**
   * Constructs [[RequestAddContact]] instance from byte array.
   */
  def read(array: Array[Byte]): RequestAddContact = {
    new RequestAddContact()
  }

}

/**
 * Sent when the user initiates adding another device as a contact.
 */
case class RequestAddContact() extends MessageBody {

  override def Type = RequestAddContact.Type

  override def write: Array[Byte] = {
    val b = ByteBuffer.allocate(length)
    b.array()
  }

  override def length = 4

}
