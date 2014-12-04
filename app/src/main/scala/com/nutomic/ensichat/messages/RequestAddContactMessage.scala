package com.nutomic.ensichat.messages

import java.util.Date

import com.nutomic.ensichat.activities.AddContactsActivity
import com.nutomic.ensichat.aodvv2.Address
import com.nutomic.ensichat.messages.Message._
import org.msgpack.packer.Packer
import org.msgpack.unpacker.Unpacker

object RequestAddContactMessage {

  def read(sender: Address, receiver: Address, date: Date, up: Unpacker) =
    new RequestAddContactMessage(sender, receiver, date)

}

/**
 * Message sent by [[AddContactsActivity]] to notify a device that it should be added as a contact.
 */
class RequestAddContactMessage(override val sender: Address, override val receiver: Address,
                               override val date: Date) extends Message(Type.RequestAddContact) {

  override def doWrite(packer: Packer) = {
  }

  override def toString = "RequestAddContactMessage(" + sender.toString + ", " + receiver.toString +
    ", " + date.toString + ")"

}
