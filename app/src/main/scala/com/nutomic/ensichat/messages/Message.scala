package com.nutomic.ensichat.messages

import java.io.IOException
import java.util.{Date, Objects}

import com.nutomic.ensichat.aodvv2.Address
import org.msgpack.ScalaMessagePack
import org.msgpack.packer.Packer

object Message {

  /**
   * Types of messages that can be transfered.
   *
   * There must be one type for each implementation and vice versa.
   */
  object Type {
    val Text = 1
    val RequestAddContact = 2
    val ResultAddContact = 3
  }

  /**
   * Orders messages by date, oldest messages first.
   */
  val Ordering = new Ordering[Message] {
    override def compare(m1: Message, m2: Message) =  m1.date.compareTo(m2.date)
  }

  /**
   * Reads a byte array that was written by [[Message.write]] into the correct
   * message implementation..
   *
   * @return Deserialized message and sits signature.
   */
  def read(bytes: Array[Byte]): (Message, Array[Byte]) = {
    val up = new ScalaMessagePack().createBufferUnpacker(bytes)

    @throws[IOException]("If the message can't be parsed")
    @throws[RuntimeException]("If the message has an unknown type")
    val messageType = up.readInt()
    val sender      = new Address(up.readByteArray())
    val receiver    = new Address(up.readByteArray())
    val date        = new Date(up.readLong())
    val sig         = up.readByteArray()
    (messageType match {
      case Type.Text              => TextMessage.read(sender, receiver, date, up)
      case Type.RequestAddContact => RequestAddContactMessage.read(sender, receiver, date, up)
      case Type.ResultAddContact  => ResultAddContactMessage.read(sender, receiver, date, up)
      case t =>
        throw new RuntimeException("Received message of unknown type " + t)
    }, sig)
  }

}

/**
 * Message object that can be sent between remote devices.
 *
 * @param messageType One of [[Message.Type]].
 */
@Deprecated
abstract class Message(val messageType: Int) {

  /**
   * Device where the message was sent from.
   */
  val sender: Address

  /**
   * Device the message is addressed to.
   */
  val receiver: Address

  /**
   * Timestamp of message creation.
   */
  val date: Date

  /**
   * Writes this message and the given signature into byte array.
   *
   * Signature may not be null, but can be an empty array.
   */
  def write(signature: Array[Byte]): Array[Byte] = {
    val packer = new ScalaMessagePack().createBufferPacker()
    packer.write(messageType)
      .write(sender.Bytes)
      .write(receiver.Bytes)
      .write(date.getTime)
      .write(signature)
    doWrite(packer)
    packer.toByteArray
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
      case o: Message => sender == o.sender && receiver == o.receiver && date == o.date
      case _ => false
  }

  /**
   * Returns a hash code for this object.
   *
   * Implementations must provide their own implementation to check the result of this
   * function and their own data.
   */
  override def hashCode: Int = Objects.hash(sender, receiver, date)

  override def toString: String

}
