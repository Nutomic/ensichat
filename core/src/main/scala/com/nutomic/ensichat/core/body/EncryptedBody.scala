package com.nutomic.ensichat.core.body

/**
 * Represents the data in an encrypted message body.
 */
final case class EncryptedBody(data: Array[Byte]) extends MessageBody {

  override def protocolType = -1

  override def contentType = -1

  def write = data

  override def length = data.length

  override def toString = "EncryptedBody"
}
