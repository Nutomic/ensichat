package com.nutomic.ensichat.protocol

import android.app.{Notification, NotificationManager, PendingIntent, Service}
import android.bluetooth.BluetoothAdapter
import android.content.{Context, Intent}
import android.os.Handler
import android.preference.PreferenceManager
import android.util.Log
import com.nutomic.ensichat.R
import com.nutomic.ensichat.activities.ConfirmAddContactDialog
import com.nutomic.ensichat.bluetooth.BluetoothInterface
import com.nutomic.ensichat.fragments.SettingsFragment
import com.nutomic.ensichat.protocol.ChatService.{OnConnectionsChangedListener, OnMessageReceivedListener}
import com.nutomic.ensichat.protocol.messages._
import com.nutomic.ensichat.util.Database

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object ChatService {

  abstract class InterfaceHandler {

    def create(): Unit

    def destroy(): Unit

    def send(nextHop: Address, msg: Message): Unit

  }

  trait OnMessageReceivedListener {
    def onMessageReceived(messages: Message): Unit
  }

  /**
   * Used with [[ChatService.registerConnectionListener]], called when a Bluetooth device
   * connects or disconnects
   */
  trait OnConnectionsChangedListener {
    def onConnectionsChanged(): Unit
  }

}

/**
 * High-level handling of all message transfers and callbacks.
 */
class ChatService extends Service {

  private val Tag = "ChatService"

  lazy val database = new Database(this)

  private val mainHandler = new Handler()

  private lazy val binder = new ChatServiceBinder(this)

  private lazy val crypto = new Crypto(this)

  private lazy val btInterface = new BluetoothInterface(this, crypto, mainHandler)

  private lazy val router = new Router(connections, sendVia)

  private lazy val seqNumGenerator = new SeqNumGenerator(this)

  private val notificationIdGenerator = Stream.from(100).iterator

  /**
   * For this (and [[messageListeners]], functions would be useful instead of instances,
   * but on a Nexus S (Android 4.1.2), these functions are garbage collected even when
   * referenced.
   */
  private var connectionListeners = new mutable.WeakHashMap[OnConnectionsChangedListener, Unit].keySet

  private var messageListeners = new mutable.WeakHashMap[OnMessageReceivedListener, Unit].keySet

  /**
   * Holds all known users.
   *
   * This is for user names that were received during runtime, and is not persistent.
   */
  private var knownUsers = Set[User]()

  /**
   * Generates keys and starts Bluetooth interface.
   */
  override def onCreate(): Unit = {
    super.onCreate()

    val pm = PreferenceManager.getDefaultSharedPreferences(this)
    if (pm.getString(SettingsFragment.KeyUserName, null) == null)
      pm.edit().putString(SettingsFragment.KeyUserName,
        BluetoothAdapter.getDefaultAdapter.getName).apply()

    registerMessageListener(database)

    Future {
      crypto.generateLocalKeys()

      btInterface.create()
      Log.i(Tag, "Service started, address is " + crypto.localAddress)
    }
  }

  override def onDestroy(): Unit = {
    btInterface.destroy()
  }

  override def onStartCommand(intent: Intent, flags: Int, startId: Int) = Service.START_STICKY

  override def onBind(intent: Intent) =  binder

  /**
   * Registers a listener that is called whenever a new message is sent or received.
   */
  def registerMessageListener(listener: OnMessageReceivedListener): Unit = {
    messageListeners += listener
  }

  /**
   * Registers a listener that is called whenever a new device is connected.
   */
  def registerConnectionListener(listener: OnConnectionsChangedListener): Unit = {
    connectionListeners += listener
    listener.onConnectionsChanged()
  }

  /**
   * Sends a new message to the given target address.
   */
  def sendTo(target: Address, body: MessageBody): Unit = {
    if (!btInterface.getConnections.contains(target))
      return

    val header = new MessageHeader(body.messageType, MessageHeader.DefaultHopLimit,
      crypto.localAddress, target, seqNumGenerator.next())

    val msg = new Message(header, body)
    val encrypted = crypto.encrypt(crypto.sign(msg))
    router.onReceive(encrypted)
    onNewMessage(msg)
  }

  private def sendVia(nextHop: Address, msg: Message) =
    btInterface.send(nextHop, msg)

  /**
   * Decrypts and verifies incoming messages, forwards valid ones to [[onNewMessage()]].
   */
  def onMessageReceived(msg: Message): Unit = {
    if (msg.Header.target == crypto.localAddress) {
      val decrypted = crypto.decrypt(msg)
      if (!crypto.verify(decrypted)) {
        Log.i(Tag, "Ignoring message with invalid signature from " + msg.Header.origin)
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
  private def onNewMessage(msg: Message): Unit = msg.Body match {
    case name: UserName =>
      val contact = new User(msg.Header.origin, name.name)
      knownUsers += contact
      if (database.getContact(msg.Header.origin).nonEmpty)
        database.changeContactName(contact)

      callConnectionListeners()
    case _: RequestAddContact =>
      if (msg.Header.origin == crypto.localAddress)
        return

      Log.i(Tag, "Remote device " + msg.Header.origin +
        " wants to add us as a contact, showing notification")
      val intent = new Intent(this, classOf[ConfirmAddContactDialog])
      intent.putExtra(ConfirmAddContactDialog.ExtraContactAddress, msg.Header.origin.toString)
      val pi = PendingIntent.getActivity(this, 0, intent,
        PendingIntent.FLAG_UPDATE_CURRENT)

      val notification = new Notification.Builder(this)
        .setContentTitle(getString(R.string.notification_friend_request, getUser(msg.Header.origin)))
        .setSmallIcon(R.drawable.ic_launcher)
        .setContentIntent(pi)
        .setAutoCancel(true)
        .build()
      val nm = getSystemService(Context.NOTIFICATION_SERVICE).asInstanceOf[NotificationManager]
      nm.notify(notificationIdGenerator.next(), notification)
    case _ =>
      mainHandler.post(new Runnable {
        override def run(): Unit =
          messageListeners.foreach(_.onMessageReceived(msg))
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
   * @param msg The message containing [[ConnectionInfo]] to open the connection.
   * @return True if the connection is valid
   */
  def onConnectionOpened(msg: Message): Boolean = {
    val maxConnections = PreferenceManager
      .getDefaultSharedPreferences(this)
      .getString(SettingsFragment.MaxConnections, Int.MaxValue.toString)
      .toInt
    if (connections().size == maxConnections) {
      Log.i(Tag, "Maximum number of connections reached")
      false
    }

    val info = msg.Body.asInstanceOf[ConnectionInfo]
    val sender = crypto.calculateAddress(info.key)
    if (sender == Address.Broadcast || sender == Address.Null) {
      Log.i(Tag, "Ignoring ConnectionInfo message with invalid sender " + sender)
      false
    }

    if (crypto.havePublicKey(sender) && !crypto.verify(msg, crypto.getPublicKey(sender))) {
      Log.i(Tag, "Ignoring ConnectionInfo message with invalid signature")
      false
    }

    if (!crypto.havePublicKey(sender)) {
      crypto.addPublicKey(sender, info.key)
      Log.i(Tag, "Added public key for new device " + sender.toString)
    }

    Log.i(Tag, "Node " + sender + " connected")
    val name = PreferenceManager.getDefaultSharedPreferences(this).getString("user_name", null)
    sendTo(sender, new UserName(name))
    callConnectionListeners()
    true
  }

  /**
   * Calls all [[connectionListeners]] with the currently active connections.
   *
   * Should be called whenever a neighbor connects or disconnects.
   */
  def callConnectionListeners(): Unit = {
    connectionListeners
      .foreach(_.onConnectionsChanged())
  }

  def connections() =
    btInterface.getConnections

  def getUser(address: Address) =
    knownUsers.find(_.address == address).getOrElse(new User(address, address.toString))

}
