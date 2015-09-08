package com.nutomic.ensichat.core

import java.io.File
import java.util.Date

import com.nutomic.ensichat.core.body.{ConnectionInfo, MessageBody, UserInfo}
import com.nutomic.ensichat.core.header.ContentHeader
import com.nutomic.ensichat.core.interfaces._
import com.nutomic.ensichat.core.util.FutureHelper

import scala.concurrent.ExecutionContext.Implicits.global

/**
 * High-level handling of all message transfers and callbacks.
 */
class ConnectionHandler(settings: Settings, database: DatabaseInterface,
                        callbacks: CallbackInterface, keyFolder: File) {

  private val Tag = "ConnectionHandler"

  private lazy val crypto = new Crypto(settings, keyFolder)

  private var transmissionInterface: TransmissionInterface = _

  private lazy val router = new Router(connections, sendVia)

  private lazy val seqNumGenerator = new SeqNumGenerator(settings)

  /**
   * Holds all known users.
   *
   * This is for user names that were received during runtime, and is not persistent.
   */
  private var knownUsers = Set[User]()

  /**
   * Generates keys and starts Bluetooth interface.
   */
  def start(): Unit = {
    FutureHelper {
      crypto.generateLocalKeys()
      Log.i(Tag, "Service started, address is " + crypto.localAddress)
    }
  }

  def stop(): Unit = {
    transmissionInterface.destroy()
  }

  def setTransmissionInterface(interface: TransmissionInterface) = {
    transmissionInterface = interface
    transmissionInterface.create()
  }

  /**
   * Sends a new message to the given target address.
   */
  def sendTo(target: Address, body: MessageBody): Unit = {
    FutureHelper {
      val messageId = settings.get("message_id", 0L)
      val header = new ContentHeader(crypto.localAddress, target, seqNumGenerator.next(),
        body.contentType, Some(messageId), Some(new Date()))
      settings.put("message_id", messageId + 1)

      val msg = new Message(header, body)
      val encrypted = crypto.encrypt(crypto.sign(msg))
      router.onReceive(encrypted)
      onNewMessage(msg)
    }
  }

  private def sendVia(nextHop: Address, msg: Message) =
    transmissionInterface.send(nextHop, msg)

  /**
   * Decrypts and verifies incoming messages, forwards valid ones to [[onNewMessage()]].
   */
  def onMessageReceived(msg: Message): Unit = {
    if (msg.header.target == crypto.localAddress) {
      val decrypted = crypto.decrypt(msg)
      if (!crypto.verify(decrypted)) {
        Log.i(Tag, "Ignoring message with invalid signature from " + msg.header.origin)
        return
      }
      onNewMessage(decrypted)
    } else {
      router.onReceive(msg)
    }
  }

  /**
   * Handles all (locally and remotely sent) new messages.
   */
  private def onNewMessage(msg: Message): Unit = msg.body match {
    case ui: UserInfo =>
      val contact = new User(msg.header.origin, ui.name, ui.status)
      knownUsers += contact
      if (database.getContact(msg.header.origin).nonEmpty)
        database.updateContact(contact)

      callbacks.onConnectionsChanged()
    case _ =>
      val origin = msg.header.origin
      if (origin != crypto.localAddress && database.getContact(origin).isEmpty)
        database.addContact(getUser(origin))

      database.onMessageReceived(msg)
      callbacks.onMessageReceived(msg)
  }

  /**
   * Opens connection to a direct neighbor.
   *
   * This adds the other node's public key if we don't have it. If we do, it validates the signature
   * with the stored key.
   *
   * @param msg The message containing [[ConnectionInfo]] to open the connection.
   * @return True if the connection is valid
   */
  def onConnectionOpened(msg: Message): Boolean = {
    val maxConnections = settings.get(Settings.KeyMaxConnections, Settings.DefaultMaxConnections.toString).toInt
    if (connections().size == maxConnections) {
      Log.i(Tag, "Maximum number of connections reached")
      return false
    }

    val info = msg.body.asInstanceOf[ConnectionInfo]
    val sender = crypto.calculateAddress(info.key)
    if (sender == Address.Broadcast || sender == Address.Null) {
      Log.i(Tag, "Ignoring ConnectionInfo message with invalid sender " + sender)
      return false
    }

    if (crypto.havePublicKey(sender) && !crypto.verify(msg, crypto.getPublicKey(sender))) {
      Log.i(Tag, "Ignoring ConnectionInfo message with invalid signature")
      return false
    }

    synchronized {
      if (!crypto.havePublicKey(sender)) {
        crypto.addPublicKey(sender, info.key)
        Log.i(Tag, "Added public key for new device " + sender.toString)
      }
    }

    Log.i(Tag, "Node " + sender + " connected")
    sendTo(sender, new UserInfo(settings.get(Settings.KeyUserName, ""),
                                settings.get(Settings.KeyUserStatus, "")))
    callbacks.onConnectionsChanged()
    true
  }

  def onConnectionClosed() = callbacks.onConnectionsChanged()

  def connections() = transmissionInterface.getConnections

  def getUser(address: Address) =
    knownUsers.find(_.address == address).getOrElse(new User(address, address.toString, ""))

}
