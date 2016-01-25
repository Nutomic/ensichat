package com.nutomic.ensichat.core.internet

import java.io.{IOException, PrintStream}
import java.net.{Socket, ServerSocket}

import com.nutomic.ensichat.core.{Message, Crypto}
import com.nutomic.ensichat.core.interfaces.Log

import scala.io.BufferedSource

class InternetServerThread(crypto: Crypto, onConnected: (InternetConnectionThread) => Unit,
                   onDisconnected: (InternetConnectionThread) => Unit, onReceive: (Message, InternetConnectionThread) => Unit) extends Thread {

  private val Tag = "InternetServerThread"

  private lazy val socket: Option[ServerSocket] = try {
    Option(new ServerSocket(InternetInterface.ServerPort))
  } catch {
    case e: IOException =>
      Log.w(Tag, "Failed to create server socket", e)
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
      case e: IOException => Log.w(Tag, "Failed to accept connection", e)
    }
  }

  def cancel(): Unit = {
    try {
      socket.get.close()
    } catch {
      case e: IOException => Log.w(Tag, "Failed to close socket", e)
    }
  }

}
