package com.nutomic.ensichat.protocol

import java.util.Date

import android.app.Notification.Builder
import android.app.{Notification, NotificationManager, PendingIntent, Service}
import android.content.{Context, Intent}
import android.os.Handler
import android.preference.PreferenceManager
import android.util.Log
import com.nutomic.ensichat.R
import com.nutomic.ensichat.activities.MainActivity
import com.nutomic.ensichat.bluetooth.BluetoothInterface
import com.nutomic.ensichat.fragments.SettingsFragment
import com.nutomic.ensichat.protocol.ChatService.{OnConnectionsChangedListener, OnMessageReceivedListener}
import com.nutomic.ensichat.protocol.body.{ConnectionInfo, MessageBody, UserInfo}
import com.nutomic.ensichat.protocol.header.ContentHeader
import com.nutomic.ensichat.util.{AddContactsHandler, Database, NotificationHandler}

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object ChatService {

  val ActionStopService = "stop_service"

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

  private lazy val database = new Database(this)

  private lazy val preferences = PreferenceManager.getDefaultSharedPreferences(this)

  private val mainHandler = new Handler()

  private lazy val binder = new ChatServiceBinder(this)

  private lazy val crypto = new Crypto(this)

  private lazy val btInterface = new BluetoothInterface(this, mainHandler,
    onMessageReceived, callConnectionListeners, onConnectionOpened)

  private lazy val notificationHandler = new NotificationHandler(this)

  private lazy val addContactsHandler = new AddContactsHandler(this, getUser, crypto.localAddress)

  private lazy val router = new Router(connections, sendVia)

  private lazy val seqNumGenerator = new SeqNumGenerator(this)

  /**
   * For this (and [[messageListeners]], functions would be useful instead of instances,
   * but on a Nexus S (Android 4.1.2), these functions are garbage collected even when
   * referenced.
   */
  private var connectionListeners = new mutable.WeakHashMap[OnConnectionsChangedListener, Unit].keySet

  private var messageListeners = new mutable.WeakHashMap[OnMessageReceivedListener, Unit].keySet

  private lazy val notificationManager =
    getSystemService(Context.NOTIFICATION_SERVICE).asInstanceOf[NotificationManager]

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

    showPersistentNotification()

    Future {
      crypto.generateLocalKeys()
      registerMessageListener(database)
      registerMessageListener(notificationHandler)
      registerMessageListener(addContactsHandler)

      btInterface.create()
      Log.i(Tag, "Service started, address is " + crypto.localAddress)
    }
  }

  def showPersistentNotification(): Unit = {
    val openIntent = PendingIntent.getActivity(this, 0, new Intent(this, classOf[MainActivity]), 0)
    val notification = new Builder(this)
      .setSmallIcon(R.drawable.ic_launcher)
      .setContentTitle(getString(R.string.app_name))
      .setContentIntent(openIntent)
      .setOngoing(true)
      .setPriority(Notification.PRIORITY_MIN)
      .build()
    notificationManager.notify(NotificationHandler.NotificationIdRunning, notification)
  }

  override def onDestroy(): Unit = {
    notificationManager.cancel(NotificationHandler.NotificationIdRunning)
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

    val messageId = preferences.getLong("message_id", 0)
    val header = new ContentHeader(crypto.localAddress, target, seqNumGenerator.next(),
      body.contentType, messageId, new Date())
    preferences.edit().putLong("message_id", messageId + 1)

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

      callConnectionListeners()
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
    val maxConnections = preferences.getString(SettingsFragment.MaxConnections,
      getResources.getString(R.string.default_max_connections)).toInt
    if (connections().size == maxConnections) {
      Log.i(Tag, "Maximum number of connections reached")
      false
    }

    val info = msg.body.asInstanceOf[ConnectionInfo]
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
    sendTo(sender, new UserInfo(preferences.getString(SettingsFragment.KeyUserName, ""),
                                preferences.getString(SettingsFragment.KeyUserStatus, "")))
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
    knownUsers.find(_.address == address).getOrElse(new User(address, address.toString, ""))

}
