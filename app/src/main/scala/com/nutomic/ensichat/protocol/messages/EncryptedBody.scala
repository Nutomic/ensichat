package com.nutomic.ensichat.protocol.messages

/**
 * Represents the data in an encrypted message body.
 */
case class EncryptedBody(Data: Array[Byte]) extends MessageBody {

  override def Type = -1

  def write = Data

  override def length = Data.length
}
