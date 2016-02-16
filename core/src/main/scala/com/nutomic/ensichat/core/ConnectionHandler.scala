package com.nutomic.ensichat.core

import java.util.Date

import com.nutomic.ensichat.core.body.{ConnectionInfo, MessageBody, UserInfo}
import com.nutomic.ensichat.core.header.ContentHeader
import com.nutomic.ensichat.core.interfaces._
import com.nutomic.ensichat.core.internet.InternetInterface
import com.nutomic.ensichat.core.util.FutureHelper

import scala.concurrent.ExecutionContext.Implicits.global

/**
 * High-level handling of all message transfers and callbacks.
 *
 * @param maxInternetConnections Maximum number of concurrent connections that should be opened by
 *                               [[InternetInterface]].
 */
final class ConnectionHandler(settings: SettingsInterface, database: DatabaseInterface,
                              callbacks: CallbackInterface, crypto: Crypto,
                              maxInternetConnections: Int) {

  private val Tag = "ConnectionHandler"

  private var transmissionInterfaces = Set[TransmissionInterface]()

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
      Log.i(Tag, "Local user is " + settings.get(SettingsInterface.KeyUserName, "none") +
        " with status '" + settings.get(SettingsInterface.KeyUserStatus, "") + "'")
      transmissionInterfaces += new InternetInterface(this, crypto, settings, maxInternetConnections)
      transmissionInterfaces.foreach(_.create())
    }
  }

  def stop(): Unit = {
    transmissionInterfaces.foreach(_.destroy())
  }

  /**
   * NOTE: This *must* be called before [[start()]], or it will have no effect.
   */
  def addTransmissionInterface(interface: TransmissionInterface) = {
    transmissionInterfaces += interface
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
      val encrypted = crypto.encryptAndSign(msg)
      router.forwardMessage(encrypted)
      onNewMessage(msg)
    }
  }

  private def sendVia(nextHop: Address, msg: Message) =
    transmissionInterfaces.foreach(_.send(nextHop, msg))

  /**
   * Decrypts and verifies incoming messages, forwards valid ones to [[onNewMessage()]].
   */
  def onMessageReceived(msg: Message): Unit = {
    if (router.isMessageSeen(msg)) {
      Log.v(Tag, "Ignoring message from " + msg.header.origin + " that we already received")
    } else if (msg.header.target == crypto.localAddress) {
      crypto.verifyAndDecrypt(msg) match {
        case Some(m) => onNewMessage(m)
        case None => Log.i(Tag, "Ignoring message with invalid signature from " + msg.header.origin)
      }
    } else {
      router.forwardMessage(msg)
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
    val maxConnections = settings.get(SettingsInterface.KeyMaxConnections,
      SettingsInterface.DefaultMaxConnections.toString).toInt
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

    if (crypto.havePublicKey(sender) && !crypto.verify(msg, Option(crypto.getPublicKey(sender)))) {
      Log.i(Tag, "Ignoring ConnectionInfo message with invalid signature")
      return false
    }

    synchronized {
      if (!crypto.havePublicKey(sender)) {
        crypto.addPublicKey(sender, info.key)
        Log.i(Tag, "Added public key for new device " + sender.toString)
      }
    }

    // Log with username if we know it.
    if (allKnownUsers().map(_.address).contains(sender))
      Log.i(Tag, "Node " + getUser(sender).name + " (" + sender + ") connected")
    else
      Log.i(Tag, "Node " + sender + " connected")

    sendTo(sender, new UserInfo(settings.get(SettingsInterface.KeyUserName, ""),
                                settings.get(SettingsInterface.KeyUserStatus, "")))
    callbacks.onConnectionsChanged()
    true
  }

  def onConnectionClosed() = callbacks.onConnectionsChanged()

  def connections(): Set[Address] = transmissionInterfaces.flatMap(_.getConnections)

  private def allKnownUsers() = database.getContacts ++ knownUsers

  /**
   * Returns [[User]] object containing the user's name (if we know it).
   */
  def getUser(address: Address) =
    allKnownUsers()
      .find(_.address == address)
      .getOrElse(new User(address, address.toString(), ""))

  def internetConnectionChanged(): Unit = {
    transmissionInterfaces
      .find(_.isInstanceOf[InternetInterface])
      .foreach(_.asInstanceOf[InternetInterface].connectionChanged())
  }
}
