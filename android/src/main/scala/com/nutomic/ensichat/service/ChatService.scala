package com.nutomic.ensichat.service

import java.io.File

import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.content.{Context, Intent, IntentFilter}
import android.net.ConnectivityManager
import android.os.Handler
import com.nutomic.ensichat.bluetooth.BluetoothInterface
import com.nutomic.ensichat.core.{ConnectionHandler, Crypto}
import com.nutomic.ensichat.util.{Database, NetworkChangedReceiver, SettingsWrapper}

object ChatService {

  case class Binder(service: ChatService) extends android.os.Binder

  private def keyFolder(context: Context) = new File(context.getFilesDir, "keys")
  def newCrypto(context: Context) = new Crypto(new SettingsWrapper(context), keyFolder(context))

  val ActionNetworkChanged = "network_changed"

}

class ChatService extends Service {

  private lazy val binder = new ChatService.Binder(this)

  private lazy val notificationHandler = new NotificationHandler(this)

  private val callbackHandler = new CallbackHandler(this, notificationHandler)

  private lazy val connectionHandler =
    new ConnectionHandler(new SettingsWrapper(this), new Database(this), callbackHandler,
                          ChatService.newCrypto(this))

  private val networkReceiver = new NetworkChangedReceiver()

  override def onBind(intent: Intent) =  binder

  override def onStartCommand(intent: Intent, flags: Int, startId: Int): Int = {
    Option(intent).foreach { i =>
      if (i.getAction == ChatService.ActionNetworkChanged)
          connectionHandler.internetConnectionChanged()
    }

    Service.START_STICKY
  }

  /**
   * Generates keys and starts Bluetooth interface.
   */
  override def onCreate(): Unit = {
    super.onCreate()
    notificationHandler.updatePersistentNotification(getConnectionHandler.connections().size)
    if (Option(BluetoothAdapter.getDefaultAdapter).isDefined) {
      connectionHandler.addTransmissionInterface(new BluetoothInterface(this, new Handler(),
        connectionHandler))
    }
    connectionHandler.start()
    registerReceiver(networkReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION))
  }

  override def onDestroy(): Unit = {
    notificationHandler.stopPersistentNotification()
    connectionHandler.stop()
    unregisterReceiver(networkReceiver)
  }

  def getConnectionHandler = connectionHandler

}