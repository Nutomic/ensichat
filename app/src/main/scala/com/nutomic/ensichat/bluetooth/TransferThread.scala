package com.nutomic.ensichat.bluetooth

import java.io._
import java.util.Date

import android.bluetooth.BluetoothSocket
import android.util.Log
import com.nutomic.ensichat.messages.{Crypto, DeviceInfoMessage, Message, TextMessage}

/**
 * Transfers data between connnected devices.
 *
 * @param device The bluetooth device to interact with.
 * @param socket An open socket to the given device.
 * @param encrypt Object used to handle signing and encryption of messages.
 * @param onReceive Called when a message was received from the other device.
 */
class TransferThread(device: Device, socket: BluetoothSocket, localDevice: Device.ID,
                     encrypt: Crypto, onReceive: (Message) => Unit) extends Thread {

  val Tag: String = "TransferThread"

  val InStream: InputStream =
    try {
      socket.getInputStream
    } catch {
      case e: IOException =>
        Log.e(Tag, "Failed to open stream", e)
        null
    }

  val OutStream: OutputStream =
    try {
      socket.getOutputStream
    } catch {
      case e: IOException =>
        Log.e(Tag, "Failed to open stream", e)
        null
    }

  override def run(): Unit = {
    Log.i(Tag, "Starting data transfer with " + device.toString)

    // Keep listening to the InputStream while connected
    while (true) {
      try {
        val (msg, signature) = Message.read(InStream)
        var messageValid = true

        if (msg.sender != device.id) {
          Log.i(Tag, "Dropping message with invalid sender from " + device.id)
          messageValid = false
        }

        if (msg.receiver != localDevice) {
          Log.i(Tag, "Dropping message with different receiver from " + device.id)
          messageValid = false
        }

        // Add public key for new, local device.
        // Explicitly check that message was not forwarded or spoofed.
        if (msg.isInstanceOf[DeviceInfoMessage] && !encrypt.havePublicKey(msg.sender) &&
            msg.sender == device.id) {
          val dim = msg.asInstanceOf[DeviceInfoMessage]
          // Permanently store public key for new local devices (also check signature).
          if (msg.sender == device.id && encrypt.isValidSignature(msg, signature, dim.publicKey)) {
            encrypt.addPublicKey(device.id, msg.asInstanceOf[DeviceInfoMessage].publicKey)
            Log.i(Tag, "Added public key for new device " + device.name)
          }
        }

        if (!encrypt.isValidSignature(msg, signature)) {
          Log.i(Tag, "Dropping message with invalid signature from " + device.id)
          messageValid = false
        }

        if (messageValid) {
          msg match {
            case m: TextMessage => onReceive(m)
            case m: DeviceInfoMessage => encrypt.addPublicKey(msg.sender, m.publicKey)
          }
        }
      } catch {
        case e: IOException =>
          Log.e(Tag, "Disconnected from device", e)
          return
      }
    }
  }

  def send(message: Message): Unit = {
    try {
      val sig = encrypt.calculateSignature(message)
      message.write(OutStream, sig)
    } catch {
      case e: IOException =>
        Log.e(Tag, "Failed to write message", e)
    }
  }

  def close(): Unit = {
    try {
      socket.close()
    } catch {
      case e: IOException =>
        Log.e(Tag, "Failed to close socket", e);
    }
  }

}
