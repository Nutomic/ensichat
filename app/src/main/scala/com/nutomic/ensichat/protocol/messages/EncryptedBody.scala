package com.nutomic.ensichat.protocol.messages

/**
 * Represents the data in an encrypted message body.
 */
case class EncryptedBody(data: Array[Byte]) extends MessageBody {

  override def messageType = -1

  def write = data

  override def length = data.length
}
