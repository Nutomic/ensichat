package com.nutomic.ensichat.messages

import java.io.{InputStream, OutputStream}
import java.util.Date

import com.nutomic.ensichat.bluetooth.Device
import org.msgpack.ScalaMessagePack

object TextMessage {

  val Ordering = new Ordering[TextMessage] {
    override def compare(m1: TextMessage, m2: TextMessage) =  m1.date.compareTo(m2.date)
  }

  /**
   * Constructs a new message from stream.
   */
  def fromStream(in: InputStream): TextMessage = {
    val up = new ScalaMessagePack().createUnpacker(in)
    new TextMessage(
      new Device.ID(up.read(classOf[String])),
      new Device.ID(up.read(classOf[String])),
      up.read(classOf[String]),
      new Date(up.read(classOf[Long])))
  }

}

/**
 * Represents content and metadata that can be transferred between devices.
 */
class TextMessage(val sender: Device.ID, val receiver: Device.ID,
                       val text: String, val date: Date) {

  /**
   * Writes this object into stream.
   */
  def write(os: OutputStream): Unit = {
    new ScalaMessagePack().createPacker(os)
      .write(sender.toString)
      .write(receiver.toString)
      .write(text)
      .write(date.getTime)
  }

  override def equals(a: Any) = a match {
    case o: TextMessage =>
      sender == o.sender && receiver == o.receiver && text == o.text && date == o.date
    case _ => false
  }

  override def hashCode() = sender.hashCode + receiver.hashCode + text.hashCode + date.hashCode()

  override def toString = "TextMessage(" + sender.toString + ", " + receiver.toString +
    ", " + text + ", " + date.toString + ")"
}