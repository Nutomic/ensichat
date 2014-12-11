package com.nutomic.ensichat.aodvv2

/**
 * Represents the data in an encrypted message body.
 */
class EncryptedBody(val Data: Array[Byte]) extends MessageBody {

  override def Type = -1

  def write = Data

  override def toString = "EncryptedBody(Data.length=" + Data.length + ")"

  override def length = Data.length
}
