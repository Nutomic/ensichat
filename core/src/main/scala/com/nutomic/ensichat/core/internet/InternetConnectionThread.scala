package com.nutomic.ensichat.core.internet

import java.io.{IOException, InputStream, OutputStream}
import java.net.{InetAddress, Socket}

import com.nutomic.ensichat.core.Message.ReadMessageException
import com.nutomic.ensichat.core.body.ConnectionInfo
import com.nutomic.ensichat.core.header.MessageHeader
import com.nutomic.ensichat.core.interfaces.Log
import com.nutomic.ensichat.core.{Address, Crypto, Message}

/**
 * Encapsulates an active connection to another node.
 */
class InternetConnectionThread(socket: Socket, crypto: Crypto, onDisconnected: (InternetConnectionThread) => Unit,
                       onReceive: (Message, InternetConnectionThread) => Unit) extends Thread {

  private val Tag = "InternetConnectionThread"

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

  def internetAddress(): InetAddress = {
    socket.getInetAddress
  }
  
  override def run(): Unit = {
    Log.i(Tag, "Connection opened to " + socket.getInetAddress)

    send(crypto.sign(new Message(new MessageHeader(ConnectionInfo.Type,
      Address.Null, Address.Null, 0), new ConnectionInfo(crypto.getLocalPublicKey))))

    try {
      socket.setKeepAlive(true)
      while (socket.isConnected) {
        val msg = Message.read(inStream)
        Log.v(Tag, "Received " + msg)

        onReceive(msg, this)
      }
    } catch {
      case e @ (_: ReadMessageException | _: IOException) =>
        Log.w(Tag, "Failed to read incoming message", e)
        close()
        return
    }
    close()
  }

  def send(msg: Message): Unit = {
    try {
      outStream.write(msg.write)
    } catch {
      case e: IOException => Log.e(Tag, "Failed to write message", e)
    }
  }

  def close(): Unit = {
    try {
      socket.close()
    } catch {
      case e: IOException => Log.w(Tag, "Failed to close socket", e)
    }
    Log.d(Tag, "Connection to " + socket.getInetAddress + " closed")
    onDisconnected(this)
  }

}
