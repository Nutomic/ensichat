package com.nutomic.ensichat.bluetooth

import java.io.{OutputStream, InputStream}

import android.bluetooth.BluetoothSocket
import android.util.Log
import com.nutomic.ensichat.Message
import java.io.IOException
/**
 * Transfers data between connnected devices.
 *
 * @param device The bluetooth device to interact with.
 * @param socket An open socket to the given device.
 * @param onReceive Called when a message was received from the other device.
 */
class TransferThread(device: Device, socket: BluetoothSocket,
                     onReceive: (Device.ID, Message) => Unit) extends Thread {

  val Tag: String = "TransferThread"

  val InStream: InputStream =
    try {
      socket.getInputStream()
    } catch {
      case e: IOException =>
      Log.e(Tag, "Failed to open stream", e)
      null
    }

  val OutStream: OutputStream =
    try {
      socket.getOutputStream()
    } catch {
      case e: IOException =>
      Log.e(Tag, "Failed to open stream", e)
      null
    }

  override def run(): Unit = {
    var buffer: Array[Byte] = new Array(1024)

    // Keep listening to the InputStream while connected
    while (true) {
      try {
        InStream.read(buffer)
        val msg: Message = Message.fromByteArray(buffer)
        onReceive(device.id, msg)
      } catch {
        case e: IOException =>
        Log.e(Tag, "Disconnected from device", e);
        return
      }
    }
  }

  def send(message: Message): Unit = {
    try {
      OutStream.write(message.toByteArray())
    } catch {
      case e: IOException =>
        Log.e(Tag, "Failed to write message", e);
    }
  }

  def cancel(): Unit = {
    try {
      socket.close()
    } catch {
      case e: IOException =>
      Log.e(Tag, "Failed to close socket", e);
    }
  }

}
