package com.nutomic.ensichat.messages

import java.io.{InputStream, OutputStream}
import java.nio.ByteBuffer
import java.util.Date

import com.nutomic.ensichat.bluetooth.Device
import org.msgpack.ScalaMessagePack
import org.msgpack.packer.Packer

object Message {

  /**
   * Types of messages that can be transfered.
   *
   * There must be one type for each implementation.
   */
  object Type {
    val Text = 1
    val DeviceInfo = 2
  }

  /**
   * Orders messages by date, oldest messages first.
   */
  val Ordering = new Ordering[Message] {
    override def compare(m1: Message, m2: Message) =  m1.date.compareTo(m2.date)
  }

  /**
   * Deserializes a stream that was written by [[Message.write]] into the correct subclass.
   *
   * @return Deserialized message and sits signature.
   */
  def read(in: InputStream): (Message, Array[Byte]) = {
    val up = new ScalaMessagePack().createUnpacker(in)
    val messageType = up.readInt()
    val sender = new Device.ID(up.readString())
    val receiver = new Device.ID(up.readString())
    val date = new Date(up.readLong())
    val sig = up.readByteArray()
    (messageType match {
      case Type.Text       => TextMessage.read(sender, receiver, date, up)
      case Type.DeviceInfo => DeviceInfoMessage.read(sender, receiver, date, up)
    }, sig)
  }

}

/**
 * Message object that can be sent between remote devices.
 *
 * @param messageType One of [[Message.Type]].
 */
abstract class Message(messageType: Int) {

  /**
   * Device where the message was sent from.
   */
  val sender: Device.ID

  /**
   * Device the message is addressed to.
   */
  val receiver: Device.ID

  /**
   * Timestamp of message creation.
   */
  val date: Date

  /**
   * Serializes this message and the given signature into stream.
   */
  def write(os: OutputStream, signature: Array[Byte]): Unit = {
    val packer = new ScalaMessagePack().createPacker(os)
      .write(messageType)
      .write(sender.toString)
      .write(receiver.toString)
      .write(date.getTime)
      .write(signature)
     doWrite(packer)
  }

  /**
   * Serializes any extra data for implementing classes.
   */
  protected def doWrite(packer: Packer): Unit

  /**
   * Returns true if objects are equal.
   *
   * Implementations must provide their own implementation to check the result of this
   * function and their own data.
   */
  override def equals(a: Any): Boolean = a match {
      case o: TextMessage =>
        sender == o.sender && receiver == o.receiver && date == o.date
      case _ => false
  }

  /**
   * Returns a hash code for this object.
   *
   * Implementations must provide their own implementation to check the result of this
   * function and their own data.
   */
  override def hashCode: Int = sender.hashCode + receiver.hashCode + date.hashCode

  override def toString: String

  /**
   * Returns this object's data encoded as Array[Byte].
   *
   * Implementations must provide their own implementation to check the result of this
   * function and their own data.
   */
  def getBytes: Array[Byte] = intToBytes(messageType) ++ sender.toString.getBytes ++
    receiver.toString.getBytes ++ longToBytes(date.getTime)

  private def intToBytes(i: Int) = ByteBuffer.allocate(java.lang.Integer.SIZE / 8).putInt(i).array()

  private def longToBytes(l: Long) = ByteBuffer.allocate(java.lang.Long.SIZE / 8).putLong(l).array()

}
