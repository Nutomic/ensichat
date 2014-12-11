package com.nutomic.ensichat.aodvv2

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

class Message(val Header: MessageHeader, val Crypto: CryptoData, val Body: MessageBody) {

  def this(header: MessageHeader, body: MessageBody) =
    this(header, new CryptoData(None, None), body)

  def write = Header.write(Body.length + Crypto.length) ++ Crypto.write ++ Body.write

  override def toString = "Message(Header=" + Header + ", Body=" + Body + ", Crypto=" + Crypto + ")"

  override def equals(a: Any): Boolean = a match {
    case o: Message => Header == o.Header && Body == o.Body && Crypto == o.Crypto
    case _ => false
  }

}