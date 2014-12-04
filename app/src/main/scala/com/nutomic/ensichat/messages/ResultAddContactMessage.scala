package com.nutomic.ensichat.messages

import java.util.{Date, Objects}

import com.nutomic.ensichat.activities.AddContactsActivity
import com.nutomic.ensichat.aodvv2.Address
import com.nutomic.ensichat.messages.Message._
import org.msgpack.packer.Packer
import org.msgpack.unpacker.Unpacker

object ResultAddContactMessage {

  def read(sender: Address, receiver: Address, date: Date, up: Unpacker) =
    new ResultAddContactMessage(sender, receiver, date, up.readBoolean())

}

/**
 * Message sent by [[AddContactsActivity]] to tell a device whether the user confirmed adding it
 * to contacts.
 */
class ResultAddContactMessage(override val sender: Address, override val receiver: Address,
                              override val date: Date, val Accepted: Boolean)
  extends Message(Type.ResultAddContact) {

  override def doWrite(packer: Packer) = packer.write(Accepted)

  override def equals(a: Any) =
    super.equals(a) && a.asInstanceOf[ResultAddContactMessage].Accepted == Accepted

  override def hashCode =
    Objects.hash(super.hashCode: java.lang.Integer, Accepted: java.lang.Boolean)

  override def toString = "ResultAddContactMessage(" + sender.toString + ", " + receiver.toString +
    ", " + date.toString + ", " + Accepted + ")"

}
