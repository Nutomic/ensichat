package com.nutomic.ensichat.core.internet

import java.io.{IOException, InputStream, OutputStream}
import java.net.{InetAddress, Socket}

import com.nutomic.ensichat.core.Message.ReadMessageException
import com.nutomic.ensichat.core.body.ConnectionInfo
import com.nutomic.ensichat.core.header.MessageHeader
import com.nutomic.ensichat.core.{Address, Crypto, Message}
import com.typesafe.scalalogging.Logger

/**
 * Encapsulates an active connection to another node.
 */
private[core] class InternetConnectionThread(socket: Socket, crypto: Crypto,
                                             onDisconnected: (InternetConnectionThread) => Unit,
                                             onReceive: (Message, InternetConnectionThread) => Unit)
                                             extends Thread {

  private val logger = Logger(this.getClass)

  private val inStream: InputStream =
    try {
      socket.getInputStream
    } catch {
      case e: IOException =>
        logger.error("Failed to open stream", e)
        close()
        null
    }

  private val outStream: OutputStream =
    try {
      socket.getOutputStream
    } catch {
      case e: IOException =>
        logger.error("Failed to open stream", e)
        close()
        null
    }

  def internetAddress(): InetAddress = {
    socket.getInetAddress
  }
  
  override def run(): Unit = {
    logger.info("Connection opened to " + socket.getInetAddress)

    send(crypto.sign(new Message(new MessageHeader(ConnectionInfo.Type,
      Address.Null, Address.Null, 0), new ConnectionInfo(crypto.getLocalPublicKey))))

    try {
      socket.setKeepAlive(true)
      while (socket.isConnected) {
        val msg = Message.read(inStream)
        logger.trace("Received " + msg)

        onReceive(msg, this)
      }
    } catch {
      case e @ (_: ReadMessageException | _: IOException) =>
        logger.warn("Failed to read incoming message", e)
        close()
        return
    }
    close()
  }

  def send(msg: Message): Unit = {
    try {
      outStream.write(msg.write)
    } catch {
      case e: IOException => logger.error("Failed to write message", e)
    }
  }

  def close(): Unit = {
    try {
      socket.close()
    } catch {
      case e: IOException => logger.warn("Failed to close socket", e)
    }
    onDisconnected(this)
  }

}
