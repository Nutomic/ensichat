package com.nutomic.ensichat.bluetooth

import java.io.{IOException, InputStream, OutputStream}

import android.bluetooth.BluetoothSocket
import android.util.Log
import com.nutomic.ensichat.messages.TextMessage

/**
 * Transfers data between connnected devices.
 *
 * @param device The bluetooth device to interact with.
 * @param socket An open socket to the given device.
 * @param onReceive Called when a message was received from the other device.
 */
class TransferThread(device: Device, socket: BluetoothSocket,
                     onReceive: (TextMessage) => Unit) extends Thread {

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
        val msg = TextMessage.fromStream(InStream)
        onReceive(msg)
      } catch {
        case e: IOException =>
        Log.e(Tag, "Disconnected from device", e)
        return
      }
    }
  }

  def send(message: TextMessage): Unit = {
    try {
      message.write(OutStream)
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
