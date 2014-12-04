package com.nutomic.ensichat.messages

import java.util.{Date, Objects}

import com.nutomic.ensichat.aodvv2.Address
import com.nutomic.ensichat.messages.Message._
import org.msgpack.packer.Packer
import org.msgpack.unpacker.Unpacker

object TextMessage {

  def read(sender: Address, receiver: Address, date: Date, up: Unpacker): TextMessage =
    new TextMessage(sender, receiver, date, up.readString())

}

/**
 * Message that contains text.
 */
class TextMessage(override val sender: Address, override val receiver: Address,
                  override val date: Date, val text: String) extends Message(Type.Text) {

  override def doWrite(packer: Packer) = packer.write(text)

  override def equals(a: Any) = super.equals(a) && a.asInstanceOf[TextMessage].text == text

  override def hashCode = Objects.hash(super.hashCode: java.lang.Integer, text)

  override def toString = "TextMessage(" + sender.toString + ", " + receiver.toString +
    ", " + date.toString + ", " + text + ")"

}
