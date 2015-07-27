package com.nutomic.ensichat.protocol

import java.io.InputStream
import java.security.spec.InvalidKeySpecException

import com.nutomic.ensichat.protocol.body._
import com.nutomic.ensichat.protocol.header.{AbstractHeader, ContentHeader, MessageHeader}

object Message {

  /**
   * Orders messages by date, oldest messages first.
   */
  val Ordering = new Ordering[Message] {
    override def compare(m1: Message, m2: Message) =  (m1.header, m2.header) match {
      case (h1: ContentHeader, h2: ContentHeader) =>
        h1.time.compareTo(h2.time)
      case _ => 0
    }
  }

  class ParseMessageException(detailMessage: String) extends RuntimeException(detailMessage) {
  }

  val Charset = "UTF-8"

  class ReadMessageException(throwable: Throwable)
    extends RuntimeException(throwable)

  /**
   * Reads the entire message (header, crypto and body) into an object.
   */
  @throws(classOf[ReadMessageException])
  def read(stream: InputStream): Message = {
    try {
      val headerBytes = new Array[Byte](MessageHeader.Length)
      stream.read(headerBytes, 0, MessageHeader.Length)
      var (header: AbstractHeader, length) = MessageHeader.read(headerBytes)

      var contentBytes = readStream(stream, length - header.length)

      if (header.isContentMessage) {
        val ret: (ContentHeader, Array[Byte]) = ContentHeader.read(header, contentBytes)
        header = ret._1
        contentBytes = ret._2
      }

      val (crypto, remaining) = CryptoData.read(contentBytes)

      val body =
        header.protocolType match {
          case ConnectionInfo.Type => ConnectionInfo.read(remaining)
          case _                   => new EncryptedBody(remaining)
        }

      new Message(header, crypto, body)
    } catch {
      case e @ (_ : OutOfMemoryError | _ : InvalidKeySpecException) =>
        throw new ReadMessageException(e)
    }
  }

  /**
   * Reads length bytes from stream and returns them.
   */
  private def readStream(stream: InputStream, length: Int): Array[Byte] = {
    val contentBytes = new Array[Byte](length)

    var numRead = 0
    do {
      numRead += stream.read(contentBytes, numRead, length - numRead)
    } while (numRead < length)
    contentBytes
  }

}

case class Message(header: AbstractHeader, crypto: CryptoData, body: MessageBody) {

  def this(header: AbstractHeader, body: MessageBody) =
    this(header, new CryptoData(None, None), body)

  def write = header.write(body.length + crypto.length) ++ crypto.write ++ body.write

}
