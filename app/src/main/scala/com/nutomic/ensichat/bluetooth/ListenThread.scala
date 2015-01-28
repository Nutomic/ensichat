package com.nutomic.ensichat.bluetooth

import java.io.IOException

import android.bluetooth.{BluetoothAdapter, BluetoothServerSocket, BluetoothSocket}
import android.util.Log

/**
 * Listens for incoming connections from other devices.
 *
 * @param name Service name to broadcast.
 */
class ListenThread(name: String, adapter: BluetoothAdapter,
                   onConnected: (Device, BluetoothSocket) => Unit) extends Thread {

  private val Tag: String = "ListenThread"

  private val ServerSocket: BluetoothServerSocket =
    try {
      adapter.listenUsingInsecureRfcommWithServiceRecord(name, BluetoothInterface.AppUuid)
    } catch {
      case e: IOException =>
        Log.e(Tag, "Failed to create listener", e)
        null
    }

  override  def run(): Unit = {
    Log.i(Tag, "Listening for connections at " + adapter.getAddress)
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
      Log.i(Tag, "Incoming connection from " + device.toString)
      onConnected(device, socket)
    }
  }

  def cancel(): Unit = {
    Log.i(Tag, "Canceling listening")
    try {
      ServerSocket.close()
    } catch {
      case e: IOException =>
        Log.e(Tag, "Failed to close listener", e)
    }
  }

}
