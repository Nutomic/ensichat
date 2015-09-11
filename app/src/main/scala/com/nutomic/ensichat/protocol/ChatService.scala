package com.nutomic.ensichat.protocol

import java.util.Date

import android.app.{Notification, NotificationManager, PendingIntent, Service}
import android.content.{Context, Intent}
import android.os.Handler
import android.preference.PreferenceManager
import android.support.v4.app.NotificationCompat
import android.support.v4.content.LocalBroadcastManager
import android.util.Log
import com.nutomic.ensichat.R
import com.nutomic.ensichat.activities.MainActivity
import com.nutomic.ensichat.bluetooth.BluetoothInterface
import com.nutomic.ensichat.fragments.SettingsFragment
import com.nutomic.ensichat.protocol.body.{ConnectionInfo, MessageBody, UserInfo}
import com.nutomic.ensichat.protocol.header.ContentHeader
import com.nutomic.ensichat.util.{Database, NotificationHandler}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object ChatService {

  val ActionStopService        = "stop_service"
  val ActionMessageReceived    = "message_received"
  val ActionConnectionsChanged = "connections_changed"

  val ExtraMessage             = "extra_message"

  abstract class InterfaceHandler {

    def create(): Unit

    def destroy(): Unit

    def send(nextHop: Address, msg: Message): Unit

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

  private lazy val router = new Router(connections, sendVia)

  private lazy val seqNumGenerator = new SeqNumGenerator(this)

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

      btInterface.create()
      Log.i(Tag, "Service started, address is " + crypto.localAddress)
    }.onFailure {case e => throw e}
  }

  def showPersistentNotification(): Unit = {
    val openIntent = PendingIntent.getActivity(this, 0, new Intent(this, classOf[MainActivity]), 0)
    val notification = new NotificationCompat.Builder(this)
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
   * Sends a new message to the given target address.
   */
  def sendTo(target: Address, body: MessageBody): Unit = {
    Future {
      val messageId = preferences.getLong("message_id", 0)
      val header = new ContentHeader(crypto.localAddress, target, seqNumGenerator.next(),
        body.contentType, Some(messageId), Some(new Date()))
      preferences.edit().putLong("message_id", messageId + 1)

      val msg = new Message(header, body)
      val encrypted = crypto.encrypt(crypto.sign(msg))
      router.onReceive(encrypted)
      onNewMessage(msg)
    }.onFailure {case e => throw e}
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
      val origin = msg.header.origin
      if (origin != crypto.localAddress && database.getContact(origin).isEmpty)
        database.addContact(getUser(origin))

      database.onMessageReceived(msg)
      notificationHandler.onMessageReceived(msg)
      val i = new Intent(ChatService.ActionMessageReceived)
      i.putExtra(ChatService.ExtraMessage, msg)
      LocalBroadcastManager.getInstance(this)
        .sendBroadcast(i)
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
    sendTo(sender, new UserInfo(preferences.getString(SettingsFragment.KeyUserName, ""),
                                preferences.getString(SettingsFragment.KeyUserStatus, "")))
    callConnectionListeners()
    true
  }

  def callConnectionListeners(): Unit = {
    LocalBroadcastManager.getInstance(this)
      .sendBroadcast(new Intent(ChatService.ActionConnectionsChanged))
  }

  def connections() =
    btInterface.getConnections

  def getUser(address: Address) =
    knownUsers.find(_.address == address).getOrElse(new User(address, address.toString, ""))

}
