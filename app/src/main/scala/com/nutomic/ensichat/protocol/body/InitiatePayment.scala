package com.nutomic.ensichat.protocol.body

import java.nio.ByteBuffer

object InitiatePayment {

  val Type = 5

  /**
   * Constructs [[InitiatePayment]] instance from byte array.
   */
  def read(array: Array[Byte]): InitiatePayment = {
    new InitiatePayment()
  }

}

/**
 * Sent to initiate a bitcoin payment. This should get a [[PaymentInformation]] as a response.
 */
case class InitiatePayment() extends MessageBody {

  override def protocolType = -1

  override def contentType = InitiatePayment.Type

  override def write: Array[Byte] = {
    val b = ByteBuffer.allocate(length)
    b.array()
  }

  override def length = 4

}
