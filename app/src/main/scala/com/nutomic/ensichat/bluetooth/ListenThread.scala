package com.nutomic.ensichat.bluetooth

import java.io.IOException

import android.bluetooth.{BluetoothAdapter, BluetoothServerSocket, BluetoothSocket}
import android.util.Log

/**
 * Listens for incoming connections from other devices.
 */
class ListenThread(name: String, adapter: BluetoothAdapter,
                   onConnected: (Device, BluetoothSocket) => Unit) extends Thread {

  val Tag: String = "ListenThread"

  val ServerSocket: BluetoothServerSocket =
    try {
      adapter.listenUsingInsecureRfcommWithServiceRecord(name, ChatService.appUuid)
    } catch {
      case e: IOException =>
        Log.e(Tag, "Failed to create listener", e)
        null
    }

  override  def run(): Unit = {
    Log.i(Tag, "Listening for connections")
    var socket: BluetoothSocket = null

    while (true) {
      try {
        // This is a blocking call and will only return on a
        // successful connection or an exception
        socket = ServerSocket.accept()
      } catch {
        case e: IOException =>
          // This happens if Bluetooth is disabled manually.
          Log.w(Tag, "Failed to accept new connection", e)
          return
      }

      val device: Device = new Device(socket.getRemoteDevice, true)
      onConnected(device, socket)
    }
  }

  def cancel(): Unit = {
    try {
      ServerSocket.close()
    } catch {
      case e: IOException =>
        Log.e(Tag, "Failed to close listener", e)
    }
  }

}
