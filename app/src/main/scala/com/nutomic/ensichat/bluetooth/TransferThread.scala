package com.nutomic.ensichat.bluetooth

import java.io._
import java.util.Date

import android.bluetooth.BluetoothSocket
import android.util.Log
import com.nutomic.ensichat.aodvv2._
import com.nutomic.ensichat.messages.Crypto

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
                     crypto: Crypto, onReceive: (MessageHeader, MessageBody, Device.ID) => Unit)
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

    send(new MessageHeader(ConnectionInfo.Type, ConnectionInfo.HopLimit, new Date(), Address.Null,
      Address.Null, 0, 0), new ConnectionInfo(crypto.getLocalPublicKey))

    while (socket.isConnected) {
      try {
        val headerBytes = new Array[Byte](MessageHeader.Length)
        InStream.read(headerBytes, 0, MessageHeader.Length)
        val header = MessageHeader.read(headerBytes)
        val bodyLength = (header.Length - MessageHeader.Length).toInt

        val bodyBytes = new Array[Byte](bodyLength)
        InStream.read(bodyBytes, 0, bodyLength)

        val body =
          header.MessageType match {
            case ConnectionInfo.Type => ConnectionInfo.read(bodyBytes)
            case Data.Type           => Data.read(bodyBytes)
          }

        onReceive(header, body, device.Id)
      } catch {
        case e: RuntimeException =>
          Log.i(Tag, "Received invalid message", e)
        case e: IOException =>
          Log.w(Tag, "Failed to read incoming message", e)
          return
      }
    }
    service.onConnectionChanged(new Device(device.bluetoothDevice, false), null)
  }

  def send(header: MessageHeader, body: MessageBody): Unit = {
    try {
      OutStream.write(header.write(body))
    } catch {
      case e: IOException => Log.e(Tag, "Failed to write message", e)
    }
  }

  def close(): Unit = {
    try {
      socket.close()
    } catch {
      case e: IOException => Log.e(Tag, "Failed to close socket", e);
    }
  }

}
