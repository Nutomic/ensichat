package com.nutomic.ensichat.bluetooth

import java.io.IOException

import android.bluetooth.{BluetoothAdapter, BluetoothSocket}
import android.util.Log

/**
 * Listens for incoming connections from other devices.
 *
 * @param name Service name to broadcast.
 */
class ListenThread(name: String, adapter: BluetoothAdapter,
                   onConnected: (Device, BluetoothSocket) => Unit) extends Thread {

  private val Tag = "ListenThread"

  private val serverSocket =
    try {
      adapter.listenUsingInsecureRfcommWithServiceRecord(name, BluetoothInterface.AppUuid)
    } catch {
      case e: IOException =>
        Log.e(Tag, "Failed to create listener", e)
        null
    }

  override  def run(): Unit = {
    var socket: BluetoothSocket = null

    while (true) {
      try {
        // This is a blocking call and will only return on a
        // successful connection or an exception
        socket = serverSocket.accept()
      } catch {
        case e: IOException =>
          // This happens if Bluetooth is disabled manually.
          Log.w(Tag, "Failed to accept new connection", e)
          return
      }

      val device: Device = new Device(socket.getRemoteDevice, true)
      Log.i(Tag, "Incoming connection from " + device.toString)
      onConnected(device, socket)
    }
  }

  def cancel(): Unit = {
    Log.i(Tag, "Canceling listening")
    try {
      serverSocket.close()
    } catch {
      case e: IOException =>
        Log.e(Tag, "Failed to close listener", e)
    }
  }

}
