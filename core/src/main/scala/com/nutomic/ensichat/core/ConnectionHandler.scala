package com.nutomic.ensichat.core

import java.security.InvalidKeyException
import java.util.Date

import com.nutomic.ensichat.core.body._
import com.nutomic.ensichat.core.header.{ContentHeader, MessageHeader}
import com.nutomic.ensichat.core.interfaces._
import com.nutomic.ensichat.core.internet.InternetInterface
import com.nutomic.ensichat.core.util.{Database, FutureHelper, LocalRoutesInfo, RouteMessageInfo}
import com.typesafe.scalalogging.Logger

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._

/**
 * High-level handling of all message transfers and callbacks.
 *
 * @param maxInternetConnections Maximum number of concurrent connections that should be opened by
 *                               [[InternetInterface]].
 */
final class ConnectionHandler(settings: SettingsInterface, database: Database,
                              callbacks: CallbackInterface, crypto: Crypto,
                              maxInternetConnections: Int,
                              port: Int = InternetInterface.DefaultPort) {

  private val logger = Logger(this.getClass)

  private val MissingRouteMessageTimeout = 5.minutes

  private var transmissionInterfaces = Set[TransmissionInterface]()

  private lazy val seqNumGenerator = new SeqNumGenerator(settings)

  private val localRoutesInfo = new LocalRoutesInfo(connections)

  private val routeMessageInfo = new RouteMessageInfo()

  private lazy val router = new Router(localRoutesInfo,
                                       (a, m) => transmissionInterfaces.foreach(_.send(a, m)),
                                       noRouteFound)

  /**
    * Contains messages that couldn't be forwarded because we don't know a route.
    *
    * These will be buffered until we receive a [[RouteReply]] for the target, or when until the
    * message has couldn't be forwarded after [[MissingRouteMessageTimeout]].
    */
  private var missingRouteMessages = Set[(Message, Date)]()

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
        new InternetInterface(this, crypto, settings, maxInternetConnections, port)
      transmissionInterfaces.foreach(_.create())
    }
  }

  def stop(): Unit = {
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
      val header = new ContentHeader(crypto.localAddress, target, seqNumGenerator.next(),
        body.contentType, Some(messageId), Some(new Date()))
      settings.put("message_id", messageId + 1)

      val msg = new Message(header, body)
      val encrypted = crypto.encryptAndSign(msg)
      router.forwardMessage(encrypted)
      onNewMessage(msg)
    }
  }

  private def requestRoute(target: Address): Unit = {
    assert(localRoutesInfo.getRoute(target).isEmpty)
    val seqNum = seqNumGenerator.next()
    val targetSeqNum = localRoutesInfo.getRoute(target).map(_.seqNum).getOrElse(-1)
    val body = new RouteRequest(target, seqNum, targetSeqNum, 0)
    val header = new MessageHeader(body.protocolType, crypto.localAddress, Address.Broadcast, seqNum)

    val signed = crypto.sign(new Message(header, body))
    router.forwardMessage(signed)
  }

  private def replyRoute(target: Address, replyTo: Address): Unit = {
    val seqNum = seqNumGenerator.next()
    val body = new RouteReply(seqNum, 0)
    val header = new MessageHeader(body.protocolType, crypto.localAddress, replyTo, seqNum)

    val signed = crypto.sign(new Message(header, body))
    router.forwardMessage(signed)
  }

  private def routeError(address: Address, packetSource: Option[Address]): Unit =  {
    val destination = packetSource.getOrElse(Address.Broadcast)
    val header = new MessageHeader(RouteError.Type, crypto.localAddress, destination,
                                   seqNumGenerator.next())
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
      case _ =>
    }

    if (msg.header.target != crypto.localAddress) {
      router.forwardMessage(msg)
      return
    }

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

    onNewMessage(plainMsg)
  }

  /**
    * Tries to send messages in [[missingRouteMessages]] again, after we acquired a new route.
    *
    * Before checking [[missingRouteMessages]], those older than [[MissingRouteMessageTimeout]]
    * are removed.
    */
  private def resendMissingRouteMessages(): Unit = {
    // resend messages if possible
    val date = new Date()
    missingRouteMessages = missingRouteMessages.filter { e =>
      val removeTime = new Date(e._2.getTime + MissingRouteMessageTimeout.toMillis)
      removeTime.after(date)
    }

    val m = missingRouteMessages.filter(m => localRoutesInfo.getRoute(m._1.header.target).isDefined)
    m.foreach( m => router.forwardMessage(m._1))
    missingRouteMessages --= m
  }

  private def noRouteFound(message: Message): Unit = {
    if (message.header.origin == crypto.localAddress) {
      missingRouteMessages += ((message, new Date()))
      requestRoute(message.header.target)
    } else
      routeError(message.header.target, Option(message.header.origin))
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
      logger.info("Maximum number of connections reached")
      return false
    }

    val info = msg.body.asInstanceOf[ConnectionInfo]
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

    sendTo(sender, new UserInfo(settings.get(SettingsInterface.KeyUserName, ""),
                                settings.get(SettingsInterface.KeyUserStatus, "")))
    callbacks.onConnectionsChanged()
    true
  }

  def onConnectionClosed(address: Address): Unit = {
    localRoutesInfo.connectionClosed(address)
      .foreach(routeError(_, None))
    callbacks.onConnectionsChanged()
  }

  def connections(): Set[Address] = transmissionInterfaces.flatMap(_.getConnections)

  private def allKnownUsers() = database.getContacts ++ knownUsers

  /**
   * Returns [[User]] object containing the user's name (if we know it).
   */
  def getUser(address: Address) =
    allKnownUsers()
      .find(_.address == address)
      .getOrElse(new User(address, address.toString(), ""))

  /**
    * This method should be called when the local device's internet connection has changed in any way.
    */
  def internetConnectionChanged(): Unit = {
    transmissionInterfaces
      .find(_.isInstanceOf[InternetInterface])
      .foreach(_.asInstanceOf[InternetInterface].connectionChanged())
  }
}
