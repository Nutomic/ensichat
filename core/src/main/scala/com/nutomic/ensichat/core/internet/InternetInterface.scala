package com.nutomic.ensichat.core.internet

import java.io.IOException
import java.net.{InetAddress, Socket}

import com.nutomic.ensichat.core.body.ConnectionInfo
import com.nutomic.ensichat.core.interfaces.{Log, SettingsInterface, TransmissionInterface}
import com.nutomic.ensichat.core.util.FutureHelper
import com.nutomic.ensichat.core.{Address, ConnectionHandler, Crypto, Message}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Random

object InternetInterface {

  val ServerPort = 26344

}

/**
 * Handles all Internet connectivity.
 *
 * @param maxConnections Maximum number of concurrent connections that should be opened.
 */
class InternetInterface(connectionHandler: ConnectionHandler, crypto: Crypto,
                        settings: SettingsInterface, maxConnections: Int)
  extends TransmissionInterface {

  private val Tag = "InternetInterface"

  private lazy val serverThread =
    new InternetServerThread(crypto, onConnected, onDisconnected, onReceiveMessage)

  private var connections = Set[InternetConnectionThread]()

  private var addressDeviceMap = Map[Address, InternetConnectionThread]()

  /**
   * Initializes and starts discovery and listening.
   */
  override def create(): Unit = {
    val servers = settings.get(SettingsInterface.KeyAddresses, SettingsInterface.DefaultAddresses)
      .replace("46.101.249.188:26344", SettingsInterface.DefaultAddresses)
    settings.put(SettingsInterface.KeyAddresses, servers)

    FutureHelper {
      serverThread.start()
      openAllConnections(maxConnections)
    }
  }

  /**
   * Stops discovery and listening.
   */
  override def destroy(): Unit = {
    serverThread.cancel()
    connections.foreach(_.close())
  }

  private def openAllConnections(maxConnections: Int): Unit = {
    val addresses = settings.get(SettingsInterface.KeyAddresses, SettingsInterface.DefaultAddresses)
      .split(",")
      .map(_.trim())
      .filterNot(_.isEmpty)

    Random.shuffle(addresses.toList)
      .take(maxConnections)
      .foreach(openConnection)
  }

  private def openConnection(addressPort: String): Unit = {
    val (address, port) =
      if (addressPort.contains(":")) {
        val split = addressPort.split(":")
        (split(0), split(1).toInt)
      } else
        (addressPort, InternetInterface.ServerPort)

    openConnection(address, port)
  }

  /**
   * Opens connection to the specified IP address in client mode.
   */
  private def openConnection(address: String, port: Int): Unit = {
    Log.i(Tag, s"Attempting connection to $address:$port")
    try {
      val socket = new Socket(InetAddress.getByName(address), port)
      val ct = new InternetConnectionThread(socket, crypto, onDisconnected, onReceiveMessage)
      connections += ct
      ct.start()
    } catch {
      case e: IOException =>
        Log.w(Tag, "Failed to open connection to " + address + ":" + port, e)
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
      Log.i(Tag, "Network has changed. Closing all connections and connecting to bootstrap nodes again")
      connections.foreach(_.close())
      openAllConnections(maxConnections)
    }
  }

}
