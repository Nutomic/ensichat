package com.nutomic.ensichat.core.internet

import java.io.IOException
import java.net.{InetAddress, Socket}

import com.nutomic.ensichat.core.body.ConnectionInfo
import com.nutomic.ensichat.core.interfaces.{Log, TransmissionInterface}
import com.nutomic.ensichat.core.util.FutureHelper
import com.nutomic.ensichat.core.{Address, ConnectionHandler, Crypto, Message}
import scala.concurrent.ExecutionContext.Implicits.global

object InternetInterface {

  val Port = 26344

  val BootstrapNodes = Set("192.168.1.104:26344", // T420
                           "46.101.249.188:26344")  // digital ocean

}

/**
 * Handles all Internet connectivity.
 */
class InternetInterface(connectionHandler: ConnectionHandler, crypto: Crypto)
  extends TransmissionInterface {

  private val Tag = "InternetInterface"

  private lazy val serverThread = new InternetServerThread(crypto, onConnected, onDisconnected, onReceiveMessage)

  private var connections = Set[InternetConnectionThread]()

  private var addressDeviceMap = Map[Address, InternetConnectionThread]()

  /**
   * Initializes and starts discovery and listening.
   */
  override def create(): Unit = {
    FutureHelper {
      serverThread.start()
      InternetInterface.BootstrapNodes.foreach(openConnection)
    }
  }

  /**
   * Stops discovery and listening.
   */
  override def destroy(): Unit = {
    serverThread.cancel()
    connections.foreach(_.close())
  }

  private def openConnection(addressPort: String): Unit = {
    val split = addressPort.split(":")
    openConnection(split(0), split(1).toInt)
  }

  /**
   * Opens connection to the specified IP address in client mode.
   */
  private def openConnection(nodeAddress: String, port: Int): Unit = {
    try {
      val socket = new Socket(InetAddress.getByName(nodeAddress), port)
      val ct = new InternetConnectionThread(socket, crypto, onDisconnected, onReceiveMessage)
      connections += ct
      ct.start()
    } catch {
      case e: IOException =>
        Log.w(Tag, "Failed to open connection to " + nodeAddress + ":" + port, e)
    }
  }

  private def onConnected(connectionThread: InternetConnectionThread): Unit = {
    connections += connectionThread
  }

  private def onDisconnected(connectionThread: InternetConnectionThread): Unit = {
    addressDeviceMap.find(_._2 == connectionThread).foreach { ad =>
      Log.d(Tag, "Connection closed to " + ad._1)
      connections -= connectionThread
      addressDeviceMap -= ad._1
      connectionHandler.onConnectionClosed()
    }
  }

  private def onReceiveMessage(msg: Message, thread: InternetConnectionThread): Unit = msg.body match {
    case info: ConnectionInfo =>
      val address = crypto.calculateAddress(info.key)
      if (address == crypto.localAddress) {
        Log.i(Tag, "Address " + address + " is me, not connecting to myself")
        thread.close()
        return
      }

      // Service.onConnectionOpened sends message, so mapping already needs to be in place.
      addressDeviceMap += (address -> thread)
      if (!connectionHandler.onConnectionOpened(msg))
        addressDeviceMap -= address
    case _ =>
      connectionHandler.onMessageReceived(msg)
  }

  /**
   * Sends the message to nextHop.
   */
  override def send(nextHop: Address, msg: Message): Unit = {
    addressDeviceMap
      .find(_._1 == nextHop)
      .foreach(_._2.send(msg))
  }

  /**
   * Returns all active Internet connections.
   */
  override def getConnections = addressDeviceMap.keySet

  def connectionChanged(): Unit = {
    FutureHelper {
      Log.i(Tag, "Network has changed. Close all connections and connect to bootstrap nodes again")
      connections.foreach(_.close())
      InternetInterface.BootstrapNodes.foreach(openConnection)
    }
  }

}
