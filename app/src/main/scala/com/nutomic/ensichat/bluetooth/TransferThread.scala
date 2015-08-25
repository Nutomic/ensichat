package com.nutomic.ensichat.bluetooth

import java.io._

import android.bluetooth.BluetoothSocket
import android.util.Log
import com.nutomic.ensichat.protocol._
import com.nutomic.ensichat.protocol.body.ConnectionInfo
import com.nutomic.ensichat.protocol.header.MessageHeader
import Message.ReadMessageException

/**
 * Transfers data between connnected devices.
 *
 * @param device The bluetooth device to interact with.
 * @param socket An open socket to the given device.
 * @param onReceive Called when a message was received from the other device.
 */
class TransferThread(device: Device, socket: BluetoothSocket, handler: BluetoothInterface,
                     crypto: Crypto, onReceive: (Message, Device.ID) => Unit) extends Thread {

  private val Tag = "TransferThread"

  val inStream: InputStream =
    try {
      socket.getInputStream
    } catch {
      case e: IOException =>
        Log.e(Tag, "Failed to open stream", e)
        null
    }

  val outStream: OutputStream =
    try {
      socket.getOutputStream
    } catch {
      case e: IOException =>
        Log.e(Tag, "Failed to open stream", e)
        null
    }

  override def run(): Unit = {
    Log.i(Tag, "Starting data transfer with " + device.toString)

    send(crypto.sign(new Message(new MessageHeader(ConnectionInfo.Type,
      Address.Null, Address.Null, 0), new ConnectionInfo(crypto.getLocalPublicKey))))

    while (socket.isConnected) {
      try {
        if (inStream.available() > 0) {
          val msg = Message.read(inStream)

          onReceive(msg, device.id)
          Log.v(Tag, "Receiving " + msg)
        }
      } catch {
        case e @ (_: ReadMessageException | _: IOException) =>
          Log.w(Tag, "Failed to read incoming message", e)
          close()
          return
      }
    }
    close()
  }

  def send(msg: Message): Unit = {
    try {
      outStream.write(msg.write)
      Log.v(Tag, "Sending " + msg)
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
      handler.onConnectionClosed(new Device(device.bluetoothDevice, false), null)
    }
  }

}
