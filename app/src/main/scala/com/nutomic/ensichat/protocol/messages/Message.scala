package com.nutomic.ensichat.protocol.messages

import java.io.InputStream
import java.security.spec.InvalidKeySpecException

object Message {

  /**
   * Orders messages by date, oldest messages first.
   */
  val Ordering = new Ordering[Message] {
    override def compare(m1: Message, m2: Message) =  (m1.Body, m2.Body) match {
      case (t1: Text, t2: Text) => t1.time.compareTo(t2.time)
      case _ => 0
    }
  }

  val Charset = "UTF-8"

  class ReadMessageException(throwable: Throwable)
    extends RuntimeException(throwable)

  def read(stream: InputStream): Message = {
    try {
      val headerBytes = new Array[Byte](MessageHeader.Length)
      stream.read(headerBytes, 0, MessageHeader.Length)
      val header = MessageHeader.read(headerBytes)

      val contentLength = (header.length - MessageHeader.Length).toInt
      val contentBytes = new Array[Byte](contentLength)
      var numRead = 0
      do {
        numRead += stream.read(contentBytes, numRead, contentLength - numRead)
      } while (numRead < contentLength)

      val (crypto, remaining) = CryptoData.read(contentBytes)

      val body =
        header.messageType match {
          case ConnectionInfo.Type => ConnectionInfo.read(remaining)
          case _ => new EncryptedBody(remaining)
        }

      new Message(header, crypto, body)
    } catch {
      case e @ (_ : OutOfMemoryError | _ : InvalidKeySpecException) =>
        throw new ReadMessageException(e)
    }
  }

}

case class Message(Header: MessageHeader, Crypto: CryptoData, Body: MessageBody) {

  def this(header: MessageHeader, body: MessageBody) =
    this(header, new CryptoData(None, None), body)

  def write = Header.write(Body.length + Crypto.length) ++ Crypto.write ++ Body.write

}