package com.nutomic.ensichat.protocol

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.util.Log
import com.nutomic.ensichat.bluetooth.BluetoothInterface
import com.nutomic.ensichat.protocol.ChatService.{OnMessageReceivedListener, OnConnectionsChangedListener}
import com.nutomic.ensichat.protocol.messages.{ConnectionInfo, Message, MessageBody, MessageHeader}
import com.nutomic.ensichat.util.Database

import scala.collection.SortedSet
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.ref.WeakReference

object ChatService {

  abstract class InterfaceHandler {

    def create(): Unit

    def destroy(): Unit

    def send(msg: Message): Unit

  }

  trait OnMessageReceivedListener {
    def onMessageReceived(messages: SortedSet[Message]): Unit
  }

  /**
   * Used with [[ChatService.registerConnectionListener]], called when a Bluetooth device
   * connects or disconnects
   */
  trait OnConnectionsChangedListener {
    def onConnectionsChanged(devices: Set[Address]): Unit
  }

}

/**
 * High-level handling of all message transfers and callbacks.
 */
class ChatService extends Service {

  private val Tag = "ChatService"

  lazy val Database = new Database(this)

  val MainHandler = new Handler()

  private lazy val Binder = new ChatServiceBinder(this)

  private lazy val Crypto = new Crypto(this)

  private lazy val BluetoothInterface = new BluetoothInterface(this, Crypto)

  /**
   * For this (and [[messageListeners]], functions would be useful instead of instances,
   * but on a Nexus S (Android 4.1.2), these functions are garbage collected even when
   * referenced.
   */
  private var connectionListeners = Set[WeakReference[OnConnectionsChangedListener]]()

  private var messageListeners = Set[WeakReference[OnMessageReceivedListener]]()

  /**
   * Generates keys and starts Bluetooth interface.
   */
  override def onCreate(): Unit = {
    super.onCreate()

    Future {
      Crypto.generateLocalKeys()
      Log.i(Tag, "Service started, address is " + Crypto.getLocalAddress)

      BluetoothInterface.create()
    }
  }

  override def onDestroy(): Unit = {
    BluetoothInterface.destroy()
  }

  override def onStartCommand(intent: Intent, flags: Int, startId: Int) = Service.START_STICKY

  override def onBind(intent: Intent) =  Binder

  /**
   * Registers a listener that is called whenever a new message is sent or received.
   */
  def registerMessageListener(listener: OnMessageReceivedListener): Unit = {
    messageListeners += new WeakReference[OnMessageReceivedListener](listener)
  }

  /**
   * Registers a listener that is called whenever a new device is connected.
   */
  def registerConnectionListener(listener: OnConnectionsChangedListener): Unit = {
    connectionListeners += new WeakReference[OnConnectionsChangedListener](listener)
    listener.onConnectionsChanged(BluetoothInterface.getConnections)
  }

  /**
   * Sends a new message to the given target address.
   */
  def sendTo(target: Address, body: MessageBody): Unit = {
    if (!BluetoothInterface.getConnections.contains(target))
      return

    val header = new MessageHeader(body.Type, MessageHeader.DefaultHopLimit,
      Crypto.getLocalAddress, target, 0, 0)

    val msg = new Message(header, body)
    val encrypted = Crypto.encrypt(Crypto.sign(msg))
    BluetoothInterface.send(encrypted)
    onNewMessage(msg)
  }

  /**
   * Decrypts and verifies incoming messages, forwards valid ones to [[onNewMessage()]].
   */
  def onMessageReceived(msg: Message): Unit = {
    val decrypted = Crypto.decrypt(msg)
    if (!Crypto.verify(decrypted)) {
      Log.i(Tag, "Ignoring message with invalid signature from " + msg.Header.Origin)
      return
    }
    onNewMessage(decrypted)
  }

  /**
   * Calls all [[OnMessageReceivedListener]]s with the new message.
   *
   * This function is called both for locally and remotely sent messages.
   */
  private def onNewMessage(msg: Message): Unit = {
    Database.addMessage(msg)
    MainHandler.post(new Runnable {
      override def run(): Unit =
        messageListeners
          .filter(_.get.nonEmpty)
          .foreach(_.apply().onMessageReceived(SortedSet(msg)(Message.Ordering)))
    })
  }

  /**
   * Opens connection to a direct neighbor.
   *
   * This adds the other node's public key if we don't have it. If we do, it validates the signature
   * with the stored key.
   *
   * The caller must invoke [[callConnectionListeners()]]
   *
   * @param infoMsg The message containing [[ConnectionInfo]] to open the connection.
   * @return True if the connection is valid
   */
  def onConnectionOpened(infoMsg: Message): Option[Address] = {
    val info = infoMsg.Body.asInstanceOf[ConnectionInfo]
    val sender = Crypto.calculateAddress(info.key)
    if (sender == Address.Broadcast || sender == Address.Null) {
      Log.i(Tag, "Ignoring ConnectionInfo message with invalid sender " + sender)
      None
    }

    if (Crypto.havePublicKey(sender) && !Crypto.verify(infoMsg, Crypto.getPublicKey(sender))) {
      Log.i(Tag, "Ignoring ConnectionInfo message with invalid signature")
      None
    }

    if (!Crypto.havePublicKey(sender)) {
      Crypto.addPublicKey(sender, info.key)
      Log.i(Tag, "Added public key for new device " + sender.toString)
    }

    Log.i(Tag, "Node " + sender + " connected")
    Some(sender)
  }

  /**
   * Calls all [[connectionListeners]] with the currently active connections.
   *
   * Should be called whenever a neighbor connects or disconnects.
   */
  def callConnectionListeners(): Unit =
    connectionListeners
      .filter(_ != None)
      .foreach(_.apply().onConnectionsChanged(BluetoothInterface.getConnections))

}