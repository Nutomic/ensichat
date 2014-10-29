package com.nutomic.ensichat.bluetooth

import java.io.IOException

import android.bluetooth.BluetoothSocket
import android.util.Log

/**
 * Attempts to connect to another device and calls [[onConnected]] on success.
 */
class ConnectThread(device: Device, onConnected: (Device, BluetoothSocket) => Unit)
    extends Thread {

  val Tag = "ConnectThread"

  val socket: BluetoothSocket =
      device.bluetoothDevice.createInsecureRfcommSocketToServiceRecord(ChatService.appUuid)

  override def run(): Unit = {
    Log.i(Tag, "Connecting to " + device.toString)
    try {
      socket.connect()
    } catch {
      case e: IOException =>
        try {
          socket.close()
        } catch {
          case e2: IOException =>
            Log.e(Tag, "Failed to close socket", e2)
        }
        return;
    }

    Log.i(Tag, "Successfully connected to device " + device.name)
    onConnected(device, socket)
  }

}
