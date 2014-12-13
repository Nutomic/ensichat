package com.nutomic.ensichat.bluetooth

import java.io._

import android.bluetooth.BluetoothSocket
import android.util.Log
import com.nutomic.ensichat.protocol._
import com.nutomic.ensichat.protocol.messages.{ConnectionInfo, Message, MessageHeader}

/**
 * Transfers data between connnected devices.
 *
 * Messages must not be longer than [[TransferThread#MaxMessageLength]] bytes.
 *
 * @param device The bluetooth device to interact with.
 * @param socket An open socket to the given device.
 * @param onReceive Called when a message was received from the other device.
 */
class TransferThread(device: Device, socket: BluetoothSocket, service: ChatService,
                     crypto: Crypto, onReceive: (Message, Device.ID) => Unit)
  extends Thread {

  private val Tag: String = "TransferThread"

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

    send(crypto.sign(new Message(new MessageHeader(ConnectionInfo.Type, ConnectionInfo.HopLimit,
      Address.Null, Address.Null, 0, 0), new ConnectionInfo(crypto.getLocalPublicKey))))

    while (socket.isConnected) {
      try {
        if (InStream.available() > 0) {
          val msg = Message.read(InStream)

          onReceive(msg, device.Id)
        }
      } catch {
        case e: RuntimeException =>
          Log.i(Tag, "Received invalid message", e)
        case e: IOException =>
          Log.w(Tag, "Failed to read incoming message", e)
          close()
          return
      }
    }
    service.onConnectionChanged(new Device(device.bluetoothDevice, false), null)
    Log.i(Tag, "Neighbor " + device + " has disconnected")
  }

  def send(msg: Message): Unit = {
    try {
      OutStream.write(msg.write)
    } catch {
      case e: IOException => Log.e(Tag, "Failed to write message", e)
    }
  }

  def close(): Unit = {
    try {
      Log.i(Tag, "Closing connection to " + device)
      socket.close()
    } catch {
      case e: IOException => Log.e(Tag, "Failed to close socket", e);
    } finally {
      service.onConnectionChanged(new Device(device.bluetoothDevice, false), null)
    }
  }

}
