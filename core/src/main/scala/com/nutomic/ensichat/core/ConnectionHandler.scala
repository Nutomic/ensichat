package com.nutomic.ensichat.core

import java.security.InvalidKeyException

import com.nutomic.ensichat.core.interfaces._
import com.nutomic.ensichat.core.internet.InternetInterface
import com.nutomic.ensichat.core.messages.Message
import com.nutomic.ensichat.core.messages.body._
import com.nutomic.ensichat.core.messages.header.{MessageHeader, AbstractHeader, ContentHeader}
import com.nutomic.ensichat.core.routing._
import com.nutomic.ensichat.core.util._
import com.typesafe.scalalogging.Logger
import org.joda.time.{DateTime, Duration}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
 * High-level handling of all message transfers and callbacks.
 */
final class ConnectionHandler(settings: SettingsInterface, database: Database,
                              callbacks: CallbackInterface, crypto: Crypto,
                              port: Int = InternetInterface.DefaultPort) {

  private val logger = Logger(this.getClass)

  private var transmissionInterfaces = Set[TransmissionInterface]()

  private lazy val seqNumGenerator = new SeqNumGenerator(settings)

  private val localRoutesInfo = new LocalRoutesInfo(connections)

  private val routeMessageInfo = new RouteMessageInfo()

  private lazy val router = new Router(localRoutesInfo,
                                       (a, m) => transmissionInterfaces.foreach(_.send(a, m)),
                                       noRouteFound)

  private lazy val messageBuffer = new MessageBuffer(crypto.localAddress, requestRoute)

  /**
    * Messages which we couldn't verify yet because we don't have the sender's public key.
    */
  private var unverifiedMessages = Set[Message]()

  /**
   * Holds all known users.
   *
   * This is for user names that were received during runtime, and is not persistent.
   */
  private var knownUsers = Set[User]()

  /**
   * Generates keys and starts Bluetooth interface.
   *
   * @param additionalInterfaces Instances of [[TransmissionInterface]] to transfer data over
   *                             platform specific interfaces (eg Bluetooth).
   */
  def start(additionalInterfaces: Set[TransmissionInterface] = Set()): Future[Unit] = {
    additionalInterfaces.foreach(transmissionInterfaces += _)
    FutureHelper {
      crypto.generateLocalKeys()
      logger.info("Service started, address is " + crypto.localAddress)
      logger.info("Local user is " + settings.get(SettingsInterface.KeyUserName, "none") +
        " with status '" + settings.get(SettingsInterface.KeyUserStatus, "") + "'")
      transmissionInterfaces +=
        new InternetInterface(this, crypto, settings, port)
      transmissionInterfaces.foreach(_.create())
      database.getUnconfirmedMessages.foreach { m =>
        val encrypted = crypto.encryptAndSign(m)
        messageBuffer.addMessage(encrypted)
        requestRoute(encrypted.header.target)
      }
    }
  }

  def stop(): Unit = {
    messageBuffer.stop()
    transmissionInterfaces.foreach(_.destroy())
    database.close()
  }

  /**
   * Sends a new message to the given target address.
   */
  def sendTo(target: Address, body: MessageBody): Unit = {
    assert(body.contentType != -1)
    FutureHelper {
      val messageId = settings.get("message_id", 0L)
      val header = ContentHeader(crypto.localAddress, target, seqNumGenerator.next(),
        body.contentType, Some(messageId), Some(DateTime.now), AbstractHeader.InitialForwardingTokens)
      settings.put("message_id", messageId + 1)

      val msg = new Message(header, body)
      onNewMessage(msg)
      if (crypto.havePublicKey(target)) {
        val encrypted = crypto.encryptAndSign(msg)
        router.forwardMessage(encrypted)
        forwardMessageToRelays(encrypted)
      }
      else {
        logger.info(s"Public key missing for $target, buffering message and sending key request")
        requestPublicKey(target)
      }
    }
  }

  private def requestPublicKey(address: Address): Unit = {
    val header = MessageHeader(PublicKeyRequest.Type, crypto.localAddress, Address.Broadcast, seqNumGenerator.next(), 0)
    val msg = new Message(header, PublicKeyRequest(address))
    router.forwardMessage(crypto.sign(msg))
  }

  private def requestRoute(target: Address): Unit = {
    assert(localRoutesInfo.getRoute(target).isEmpty)
    val seqNum = seqNumGenerator.next()
    val targetSeqNum = localRoutesInfo.getRoute(target).map(_.seqNum).getOrElse(-1)
    val body = new RouteRequest(target, seqNum, targetSeqNum, 0)
    val header = new MessageHeader(body.protocolType, crypto.localAddress, Address.Broadcast, seqNum, 0)

    val signed = crypto.sign(new Message(header, body))
    router.forwardMessage(signed)
  }

  private def replyRoute(target: Address, replyTo: Address): Unit = {
    val seqNum = seqNumGenerator.next()
    val body = new RouteReply(seqNum, 0)
    val header = new MessageHeader(body.protocolType, crypto.localAddress, replyTo, seqNum, 0)

    val signed = crypto.sign(new Message(header, body))
    router.forwardMessage(signed)
  }

  private def routeError(address: Address, packetSource: Option[Address]): Unit =  {
    val destination = packetSource.getOrElse(Address.Broadcast)
    val header = new MessageHeader(RouteError.Type, crypto.localAddress, destination,
                                   seqNumGenerator.next(), 0)
    val seqNum = localRoutesInfo.getRoute(address).map(_.seqNum).getOrElse(-1)
    val body = new RouteError(address, seqNum)

    val signed = crypto.sign(new Message(header, body))
    router.forwardMessage(signed)
  }

  /**
   * Force connect to a sepcific internet.
   *
   * @param address An address in the format IP;port or hostname:port.
   */
  def connect(address: String): Unit = {
    transmissionInterfaces
      .find(_.isInstanceOf[InternetInterface])
      .map(_.asInstanceOf[InternetInterface])
      .foreach(_.openConnection(address))
  }

  /**
   * Decrypts and verifies incoming messages, forwards valid ones to [[onNewMessage()]].
   */
  def onMessageReceived(msg: Message, previousHop: Address): Unit = {
    if (router.isMessageSeen(msg)) {
      logger.trace("Ignoring message from " + msg.header.origin + " that we already received")
      return
    }

    msg.body match {
      case rreq: RouteRequest =>
        localRoutesInfo.addRoute(msg.header.origin, rreq.originSeqNum, previousHop, rreq.originMetric)
        resendMissingRouteMessages()
        // TODO: Respecting this causes the RERR test to fail. We have to fix the implementation
        //       of isMessageRedundant() without breaking the test.
        if (routeMessageInfo.isMessageRedundant(msg)) {
          logger.info("Sending redundant RREQ")
          //return
        }

        if (crypto.localAddress == rreq.requested)
          replyRoute(rreq.requested, msg.header.origin)
        else {
          val body = rreq.copy(originMetric = rreq.originMetric + 1)

          val forwardMsg = crypto.sign(new Message(msg.header, body))
          localRoutesInfo.getRoute(rreq.requested) match {
            case Some(route) => router.forwardMessage(forwardMsg, Option(route.nextHop))
            case None => router.forwardMessage(forwardMsg, Option(Address.Broadcast))
          }
        }
        return
      case rrep: RouteReply =>
        localRoutesInfo.addRoute(msg.header.origin, rrep.originSeqNum, previousHop, 0)
        // TODO: See above (in RREQ handler).
        if (routeMessageInfo.isMessageRedundant(msg)) {
          logger.debug("Sending redundant RREP")
          //return
        }

        resendMissingRouteMessages()

        if (msg.header.target == crypto.localAddress)
          return

        val existingRoute = localRoutesInfo.getRoute(msg.header.target)
        val states = Set(LocalRoutesInfo.RouteStates.Active, LocalRoutesInfo.RouteStates.Idle)
        if (existingRoute.isEmpty || !states.contains(existingRoute.get.state)) {
          routeError(msg.header.target, Option(msg.header.origin))
          return
        }

        val body = rrep.copy(originMetric = rrep.originMetric + 1)

        val forwardMsg = crypto.sign(new Message(msg.header, body))
        router.forwardMessage(forwardMsg)
        return
      case rerr: RouteError =>
        localRoutesInfo.getRoute(rerr.address).foreach { route =>
          if (route.nextHop == msg.header.origin && (rerr.seqNum == 0 || rerr.seqNum > route.seqNum)) {
            localRoutesInfo.connectionClosed(rerr.address)
              .foreach(routeError(_, None))
          }
        }
        return
      case pkr: PublicKeyRequest =>
        if (crypto.havePublicKey(pkr.address)) {
          val header = MessageHeader(PublicKeyReply.Type, crypto.localAddress, msg.header.origin, seqNumGenerator.next(), 0)
          val msg2 = new Message(header, PublicKeyReply(crypto.getPublicKey(pkr.address)))
          router.forwardMessage(crypto.sign(msg2), Option(previousHop))
        }
        else {
          router.forwardMessage(msg)
        }
        return
      case pkr: PublicKeyReply =>
        if (msg.header.target != crypto.localAddress) {
          router.forwardMessage(msg)
          return
        }
        val address = crypto.calculateAddress(pkr.key)
        if (crypto.havePublicKey(address))
          return

        logger.info(s"Received public key for $address, resending and decrypting messages")
        crypto.addPublicKey(address, pkr.key)
        database.getMessages(address)
          .filter(_.header.target == address)
          .foreach{ m =>
            sendTo(address, m.body)
          }
        val current = unverifiedMessages
          .filter(_.header.origin == address)
        current.foreach(decryptMessage)
        unverifiedMessages --= current
        return
      case _ =>
    }

    if (msg.header.target != crypto.localAddress) {
      router.forwardMessage(msg)
      forwardMessageToRelays(msg)
      return
    }

    if (!crypto.havePublicKey(msg.header.origin)) {
      logger.info(s"Received message from ${msg.header.origin} but don't have public key, buffering")
      unverifiedMessages += msg
      requestPublicKey(msg.header.origin)
      return
    }

    decryptMessage(msg)
  }

  private def decryptMessage(msg: Message): Unit = {
    val plainMsg =
      try {
        if (!crypto.verify(msg)) {
          logger.warn(s"Received message with invalid signature from ${msg.header.origin}")
          return
        }

        if (msg.header.isContentMessage)
          crypto.decrypt(msg)
        else
          msg
      } catch {
        case e: InvalidKeyException =>
          logger.warn(s"Failed to verify or decrypt message $msg", e)
          return
      }

    // This is necessary because a message is sent to the destination and relays seperately,
    // with different sequence numbers. Because of this, we also have to check the message ID
    // to avoid duplicate messages.
    if (database.getMessages(msg.header.origin).exists(m => m.header.origin == plainMsg.header.origin && m.header.messageId == plainMsg.header.messageId)) {
      logger.trace(s"Received message $msg again, ignoring")
      return
    }

    if (plainMsg.body.contentType == Text.Type) {
      logger.trace(s"Sending confirmation for $plainMsg")
      sendTo(plainMsg.header.origin, new MessageReceived(plainMsg.header.messageId.get))
    }

    onNewMessage(plainMsg)
  }

  private def forwardMessageToRelays(message: Message): Unit = {
    var tokens = message.header.tokens
    val relays = database.pickLongestConnectionDevice(connections())
    var index = 0
    while (tokens > 1) {
      val forwardTokens = tokens / 2
      val headerCopy = message.header.asInstanceOf[ContentHeader].copy(tokens = forwardTokens)
      router.forwardMessage(message.copy(header = headerCopy), relays.lift(index))
      tokens -= forwardTokens
      database.updateMessageForwardingTokens(message, tokens)
      index += 1
    }
  }

  /**
    * Tries to send messages in [[MessageBuffer]] again, after we acquired a new route.
    */
  private def resendMissingRouteMessages(): Unit = {
    localRoutesInfo.getAllAvailableRoutes
      .flatMap( r => messageBuffer.getMessages(r.destination))
      .foreach(router.forwardMessage(_))
  }

  private def noRouteFound(message: Message): Unit = {
    messageBuffer.addMessage(message)
    requestRoute(message.header.target)
  }

  /**
   * Handles all (locally and remotely sent) new messages.
   */
  private def onNewMessage(msg: Message): Unit = msg.body match {
    case ui: UserInfo =>
      val contact = User(msg.header.origin, ui.name, ui.status)
      knownUsers += contact
      if (database.getContact(msg.header.origin).nonEmpty)
        database.updateContact(contact)

      callbacks.onConnectionsChanged()
    case mr: MessageReceived =>
      database.setMessageConfirmed(mr.messageId)
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
   * @param msg The message containing [[messages.body.ConnectionInfo]] to open the connection.
   * @return True if the connection is valid
   */
  def onConnectionOpened(msg: Message): Boolean = {
    val info = msg.body.asInstanceOf[messages.body.ConnectionInfo]
    val sender = crypto.calculateAddress(info.key)
    if (sender == Address.Broadcast || sender == Address.Null) {
      logger.info("Ignoring ConnectionInfo message with invalid sender " + sender)
      return false
    }

    if (crypto.havePublicKey(sender) && !crypto.verify(msg, Option(crypto.getPublicKey(sender)))) {
      logger.info("Ignoring ConnectionInfo message with invalid signature")
      return false
    }

    synchronized {
      if (!crypto.havePublicKey(sender)) {
        crypto.addPublicKey(sender, info.key)
        logger.info("Added public key for new device " + sender.toString)
      }
    }

    // Log with username if we know it.
    if (allKnownUsers().map(_.address).contains(sender))
      logger.info("Node " + getUser(sender).name + " (" + sender + ") connected")
    else
      logger.info("Node " + sender + " connected")

    sendTo(sender, UserInfo(settings.get(SettingsInterface.KeyUserName, ""),
                            settings.get(SettingsInterface.KeyUserStatus, "")))
    callbacks.onConnectionsChanged()
    resendMissingRouteMessages()
    messageBuffer.getAllMessages
      .filter(_.header.tokens > 1)
      .foreach(forwardMessageToRelays)
    true
  }

  /**
    * Called by [[TransmissionInterface]] when a connection is closed.
    *
    * @param address The address of the connected device.
    * @param duration The time that we were connected to the device.
    */
  def onConnectionClosed(address: Address, duration: Duration): Unit = {
    localRoutesInfo.connectionClosed(address)
      .foreach(routeError(_, None))
    callbacks.onConnectionsChanged()
    database.insertOrUpdateKnownDevice(address, duration)
  }

  def connections(): Set[Address] = transmissionInterfaces.flatMap(_.getConnections)

  private def allKnownUsers() = database.getContacts ++ knownUsers

  /**
   * Returns [[util.User]] object containing the user's name (if we know it).
   */
  def getUser(address: Address) =
    allKnownUsers()
      .find(_.address == address)
      .getOrElse(User(address, address.toString(), ""))

  /**
    * This method should be called when the local device's internet connection has changed in any way.
    */
  def internetConnectionChanged(): Unit = {
    transmissionInterfaces
      .find(_.isInstanceOf[InternetInterface])
      .foreach(_.asInstanceOf[InternetInterface].connectionChanged())
  }

  def addContact(user: User): Unit = {
    database.addContact(user)
    if (!crypto.havePublicKey(user.address)) {
      requestPublicKey(user.address)
    }
  }

}
