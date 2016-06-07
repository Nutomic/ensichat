package com.nutomic.ensichat.bluetooth

import java.io._

import android.bluetooth.{BluetoothDevice, BluetoothSocket}
import android.content.{BroadcastReceiver, Context, Intent, IntentFilter}
import android.util.Log
import com.nutomic.ensichat.core.Message.ReadMessageException
import com.nutomic.ensichat.core.body.ConnectionInfo
import com.nutomic.ensichat.core.header.MessageHeader
import com.nutomic.ensichat.core.{Address, Crypto, Message}
import org.joda.time.DateTime

/**
 * Transfers data between connnected devices.
 *
 * @param device The bluetooth device to interact with.
 * @param socket An open socket to the given device.
 * @param onReceive Called when a message was received from the other device.
 */
class BluetoothTransferThread(context: Context, device: Device, socket: BluetoothSocket,
                              handler: BluetoothInterface, crypto: Crypto,
                              onReceive: (Message, Device.ID) => Unit) extends Thread {

  private val connectionOpened = DateTime.now

  private val Tag = "TransferThread"

  private var isClosed = false

  private val inStream: InputStream =
    try {
      socket.getInputStream
    } catch {
      case e: IOException =>
        Log.e(Tag, "Failed to open stream", e)
        close()
        null
    }

  private val outStream: OutputStream =
    try {
      socket.getOutputStream
    } catch {
      case e: IOException =>
        Log.e(Tag, "Failed to open stream", e)
        close()
        null
    }

  private val disconnectReceiver = new BroadcastReceiver {
    override def onReceive(context: Context, intent: Intent): Unit = {
      val address = intent.getParcelableExtra[BluetoothDevice](BluetoothDevice.EXTRA_DEVICE).getAddress
      if (device.btDevice.get.getAddress == address) {
        Log.i(Tag, "Device with address " + address + " disconnected")
        close()
      }
    }
  }

  override def run(): Unit = {
    Log.i(Tag, "Starting data transfer with " + device.toString)

    context.registerReceiver(disconnectReceiver,
                             new IntentFilter(BluetoothDevice.ACTION_ACL_DISCONNECTED))

    send(crypto.sign(new Message(new MessageHeader(ConnectionInfo.Type,
      Address.Null, Address.Null, 0), new ConnectionInfo(crypto.getLocalPublicKey))))

    while (socket.isConnected) {
      try {
        val msg = Message.read(inStream)
        Log.v(Tag, "Received " + msg)

        onReceive(msg, device.id)
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
    if (isClosed)
      return

    isClosed = true
    context.unregisterReceiver(disconnectReceiver)
    try {
      Log.i(Tag, "Closing connection to " + device)
      inStream.close()
      outStream.close()
      socket.close()
    } catch {
      case e: IOException => Log.e(Tag, "Failed to close socket", e);
    } finally {
      handler.onConnectionClosed(connectionOpened, device.id)
    }
  }

}
