package com.nutomic.ensichat.protocol.messages

import java.io.InputStream

object Message {

  /**
   * Orders messages by date, oldest messages first.
   */
  val Ordering = new Ordering[Message] {
    override def compare(m1: Message, m2: Message) =  m1.Header.Time.compareTo(m2.Header.Time)
  }

  def read(stream: InputStream): Message = {
    val headerBytes = new Array[Byte](MessageHeader.Length)
    stream.read(headerBytes, 0, MessageHeader.Length)
    val header = MessageHeader.read(headerBytes)

    val contentLength = (header.Length - MessageHeader.Length).toInt
    val contentBytes = new Array[Byte](contentLength)
    stream.read(contentBytes, 0, contentLength)

    val (crypto, remaining) = CryptoData.read(contentBytes)

    val body =
      header.MessageType match {
        case ConnectionInfo.Type => ConnectionInfo.read(remaining)
        case _                   => new EncryptedBody(remaining)
      }

    new Message(header, crypto, body)
  }

}

case class Message(Header: MessageHeader, Crypto: CryptoData, Body: MessageBody) {

  def this(header: MessageHeader, body: MessageBody) =
    this(header, new CryptoData(None, None), body)

  def write = Header.write(Body.length + Crypto.length) ++ Crypto.write ++ Body.write

}