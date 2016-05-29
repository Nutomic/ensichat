package com.nutomic.ensichat.core.internet

import java.io.IOException
import java.net.ServerSocket

import com.nutomic.ensichat.core.{Crypto, Message}
import com.typesafe.scalalogging.Logger

class InternetServerThread(crypto: Crypto, port: Int,
                           onConnected: (InternetConnectionThread) => Unit,
                           onDisconnected: (InternetConnectionThread) => Unit,
                           onReceive: (Message, InternetConnectionThread) => Unit) extends Thread {

  private val logger = Logger(this.getClass)

  private lazy val socket: Option[ServerSocket] = try {
    Option(new ServerSocket(port))
  } catch {
    case e: IOException =>
      logger.warn("Failed to create server socket", e)
      None
  }

  override def run(): Unit = {
    try {
      while (socket.get.isBound) {
        val connection = new InternetConnectionThread(socket.get.accept(), crypto, onDisconnected, onReceive)
        onConnected(connection)
        connection.start()
      }
    } catch {
      case e: IOException => logger.warn("Failed to accept connection", e)
    }
  }

  def cancel(): Unit = {
    try {
      socket.get.close()
    } catch {
      case e: IOException => logger.warn("Failed to close socket", e)
    }
  }

}
