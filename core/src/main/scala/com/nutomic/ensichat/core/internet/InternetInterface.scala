package com.nutomic.ensichat.core.internet

import java.net.{InetAddress, Socket}

import com.nutomic.ensichat.core.body.ConnectionInfo
import com.nutomic.ensichat.core.interfaces.{SettingsInterface, TransmissionInterface}
import com.nutomic.ensichat.core.util.FutureHelper
import com.nutomic.ensichat.core.{Address, ConnectionHandler, Crypto, Message}
import com.typesafe.scalalogging.Logger

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Random

private[core] object InternetInterface {

  val DefaultPort = 26344

}

/**
 * Handles all Internet connectivity.
 *
 * @param maxConnections Maximum number of concurrent connections that should be opened.
 */
private[core] class InternetInterface(connectionHandler: ConnectionHandler, crypto: Crypto,
                        settings: SettingsInterface, maxConnections: Int, port: Int)
  extends TransmissionInterface {

  private val logger = Logger(this.getClass)

  private lazy val serverThread =
    new InternetServerThread(crypto, port, onConnected, onDisconnected, onReceiveMessage)

  private var connections = Set[InternetConnectionThread]()

  private var addressDeviceMap = Map[Address, InternetConnectionThread]()

  /**
   * Initializes and starts discovery and listening.
   */
  override def create(): Unit = {
    val servers = settings.get(SettingsInterface.KeyAddresses, SettingsInterface.DefaultAddresses)
      .replace("46.101.249.188:26344", SettingsInterface.DefaultAddresses)
    settings.put(SettingsInterface.KeyAddresses, servers)

    serverThread.start()
    openAllConnections(maxConnections)
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

  def openConnection(addressPort: String): Unit = {
    val (address, port) =
      if (addressPort.contains(":")) {
        val split = addressPort.split(":")
        (split(0), split(1).toInt)
      } else
        (addressPort, InternetInterface.DefaultPort)

    openConnection(address, port)
  }

  /**
   * Opens connection to the specified IP address in client mode.
   */
  private def openConnection(address: String, port: Int): Unit = {
    logger.info(s"Attempting connection to $address:$port")
    Future {
      val socket = new Socket(InetAddress.getByName(address), port)
      val ct = new InternetConnectionThread(socket, crypto, onDisconnected, onReceiveMessage)
      connections += ct
      ct.start()
    }.onFailure { case e =>
      logger.warn("Failed to open connection to " + address + ":" + port, e)
    }
  }

  private def onConnected(connectionThread: InternetConnectionThread): Unit = {
    connections += connectionThread
  }

  private def onDisconnected(connectionThread: InternetConnectionThread): Unit = {
    getAddressForThread(connectionThread).foreach { ad =>
      logger.trace("Connection closed to " + ad)
      connections -= connectionThread
      addressDeviceMap -= ad
      connectionHandler.onConnectionClosed(ad)
    }
  }

  private def onReceiveMessage(msg: Message, thread: InternetConnectionThread): Unit = msg.body match {
    case info: ConnectionInfo =>
      val address = crypto.calculateAddress(info.key)
      if (address == crypto.localAddress) {
        logger.info("Address " + address + " is me, not connecting to myself")
        thread.close()
        return
      }

      // Service.onConnectionOpened sends message, so mapping already needs to be in place.
      addressDeviceMap += (address -> thread)
      if (!connectionHandler.onConnectionOpened(msg))
        addressDeviceMap -= address
    case _ =>
      connectionHandler.onMessageReceived(msg, getAddressForThread(thread).get)
  }

  private def getAddressForThread(thread: InternetConnectionThread) =
    addressDeviceMap.find(_._2 == thread).map(_._1)

  /**
   * Sends the message to nextHop.
   */
  override def send(nextHop: Address, msg: Message): Unit = {
    addressDeviceMap
      .filter(_._1 == nextHop || Address.Broadcast == nextHop)
      .foreach(_._2.send(msg))
  }

  /**
   * Returns all active Internet connections.
   */
  override def getConnections = addressDeviceMap.keySet

  def connectionChanged(): Unit = {
    FutureHelper {
      logger.info("Network has changed. Closing all connections and connecting to bootstrap nodes again")
      connections.foreach(_.close())
      openAllConnections(maxConnections)
    }
  }

}
