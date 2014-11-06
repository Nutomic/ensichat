package com.nutomic.ensichat.messages

import java.security.spec.X509EncodedKeySpec
import java.security.{KeyFactory, PublicKey}
import java.util.Date

import com.nutomic.ensichat.bluetooth.Device
import com.nutomic.ensichat.messages.Message._
import org.msgpack.packer.Packer
import org.msgpack.unpacker.Unpacker

object DeviceInfoMessage {

  private val FieldPublicKey = "public-key"

  def read(sender: Device.ID, receiver: Device.ID, date: Date, up: Unpacker): DeviceInfoMessage = {
    val factory = KeyFactory.getInstance(Crypto.KeyAlgorithm)
    val key = factory.generatePublic(new X509EncodedKeySpec(up.readByteArray()))
    new DeviceInfoMessage(sender, receiver, date, key)
  }

}

/**
 * Message that contains the public key of a device.
 *
 * Used on first connection to a new (local) device for key exchange.
 */
class DeviceInfoMessage(override val sender: Device.ID, override val receiver: Device.ID,
                        override val date: Date, val publicKey: PublicKey)
  extends Message(Type.DeviceInfo) {

  override def doWrite(packer: Packer) = packer.write(publicKey.getEncoded)

  override def equals(a: Any) =
    super.equals(a) && a.asInstanceOf[DeviceInfoMessage].publicKey == publicKey

  override def hashCode = super.hashCode + publicKey.hashCode

  override def toString = "DeviceInfoMessage(" + sender.toString + ", " + receiver.toString +
    ", " + date.toString + ", " + publicKey.toString + ")"

  override def getBytes = super.getBytes ++ publicKey.getEncoded

}
