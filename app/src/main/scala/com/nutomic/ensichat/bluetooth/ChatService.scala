package com.nutomic.ensichat.bluetooth

import java.util.UUID

import android.app.Service
import android.bluetooth.{BluetoothAdapter, BluetoothDevice, BluetoothSocket}
import android.content.{BroadcastReceiver, Context, Intent, IntentFilter}
import android.os.Handler
import android.util.Log
import com.nutomic.ensichat.R
import com.nutomic.ensichat.bluetooth.ChatService.{OnDeviceConnectedListener, OnMessageReceivedListener}
import com.nutomic.ensichat.messages.{MessageStore, TextMessage}

import scala.collection.immutable.{HashMap, HashSet, TreeSet}
import scala.collection.{SortedSet, mutable}
import scala.ref.WeakReference

object ChatService {

  /**
   * Bluetooth service UUID version 5, created with namespace URL and "ensichat.nutomic.com".
   */
  val appUuid: UUID = UUID.fromString("8ed52b7a-4501-5348-b054-3d94d004656e")

  trait OnDeviceConnectedListener {
    def onDeviceConnected(devices: Map[Device.ID, Device]): Unit
  }

  trait OnMessageReceivedListener {
    def onMessageReceived(messages: SortedSet[TextMessage]): Unit
  }

}

/**
 * Handles all Bluetooth connectivity.
 */
class ChatService extends Service {

  private val Tag = "ChatService"

  private val ScanInterval = 5000

  private val Binder = new ChatServiceBinder(this)

  private var bluetoothAdapter: BluetoothAdapter = _

  /**
   * For this (and [[messageListeners]], functions would be useful instead of instances,
   * but on a Nexus S (Android 4.1.2), these functions are garbage collected even when
   * referenced.
   */
  private var deviceListeners = new HashSet[WeakReference[OnDeviceConnectedListener]]()

  private val messageListeners =
    mutable.HashMap[Device.ID, mutable.Set[WeakReference[OnMessageReceivedListener]]]()
      .withDefaultValue(mutable.Set[WeakReference[OnMessageReceivedListener]]())

  private var devices = new HashMap[Device.ID, Device]()

  private var connections = new HashMap[Device.ID, TransferThread]()

  private var ListenThread: ListenThread = _

  private var cancelDiscovery = false

  private val MainHandler = new Handler()

  private var MessageStore: MessageStore = _

  /**
   * Initializes BroadcastReceiver for discovery, starts discovery and listens for connections.
   */
  override def onCreate(): Unit = {
    super.onCreate()

    MessageStore = new MessageStore(this)

    bluetoothAdapter = BluetoothAdapter.getDefaultAdapter

    registerReceiver(DeviceDiscoveredReceiver, new IntentFilter(BluetoothDevice.ACTION_FOUND))
    registerReceiver(BluetoothStateReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))
    if (bluetoothAdapter.isEnabled) {
      startBluetoothConnections()
    }
  }

  override def onStartCommand(intent: Intent, flags: Int, startId: Int) = Service.START_STICKY

  override def onBind(intent: Intent) =  Binder

  /**
   * Stops discovery, listening and unregisters receivers.
   */
  override def onDestroy(): Unit = {
    super.onDestroy()
    ListenThread.cancel()
    cancelDiscovery = true
    unregisterReceiver(DeviceDiscoveredReceiver)
    unregisterReceiver(BluetoothStateReceiver)
  }

  /**
   * Stops any current discovery, then starts a new one, recursively until service is stopped.
   */
  def discover(): Unit = {
    if (cancelDiscovery)
      return

    if (!bluetoothAdapter.isDiscovering) {
      Log.v(Tag, "Running discovery")
      bluetoothAdapter.startDiscovery()
    }

    MainHandler.postDelayed(new Runnable {
      override def run(): Unit = discover()
    }, ScanInterval)
  }

  /**
   * Receives newly discovered devices and connects to them.
   */
  private val DeviceDiscoveredReceiver: BroadcastReceiver  = new BroadcastReceiver() {
    override def onReceive(context: Context, intent: Intent) {
      val device: Device =
        new Device(intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE), false)
      devices += (device.id -> device)
      new ConnectThread(device, onConnected).start()
    }
  }

  /**
   * Starts or stops listening and discovery based on bluetooth state.
   */
  private val BluetoothStateReceiver: BroadcastReceiver = new BroadcastReceiver {
    override def onReceive(context: Context, intent: Intent): Unit = {
      intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1) match {
        case BluetoothAdapter.STATE_ON =>
          startBluetoothConnections()
        case BluetoothAdapter.STATE_TURNING_OFF =>
          connections.foreach(d => d._2.close())
        case BluetoothAdapter.STATE_OFF =>
          Log.i(Tag, "Bluetooth disabled, stopping listening and discovery")
          if (ListenThread != null) {
            ListenThread.cancel()
          }
          cancelDiscovery = true
        case _ =>
      }
    }
  }

  /**
   * Starts to listen for incoming connections, and starts regular active discovery.
   */
  private def startBluetoothConnections(): Unit = {
    cancelDiscovery = false
    discover()
    ListenThread = new ListenThread(getString(R.string.app_name), bluetoothAdapter, onConnected)
    ListenThread.start()
  }

  /**
   * Registers a listener that is called whenever a new device is connected.
   */
  def registerDeviceListener(listener: OnDeviceConnectedListener): Unit = {
    deviceListeners += new WeakReference[OnDeviceConnectedListener](listener)
    listener.onDeviceConnected(devices)
  }

  /**
   * Called when a Bluetooth device is connected.
   *
   * Adds the device to [[connections]], notifies all [[deviceListeners]].
   */
  def onConnected(device: Device, socket: BluetoothSocket): Unit = {
    val updatedDevice: Device = new Device(device.bluetoothDevice, true)
    devices += (device.id -> updatedDevice)
    connections += (device.id -> new TransferThread(updatedDevice, socket, handleNewMessage))
    connections(device.id).start()
    deviceListeners.foreach(l => l.get match {
      case Some(_) => l.apply().onDeviceConnected(devices)
      case None => deviceListeners -= l
    })
  }

  /**
   * Sends message to the device specified as receiver,
   */
  def send(message: TextMessage): Unit = {
    connections.apply(message.receiver).send(message)
    handleNewMessage(message)
  }

  /**
   * Saves the message to database and sends it to registered listeners.
   *
   * If you want to send a new message, use [[send]].
   */
  def handleNewMessage(message: TextMessage): Unit = {
    MessageStore.addMessage(message)
    MainHandler.post(new Runnable {
      override def run(): Unit = {
        messageListeners(message.sender).foreach(l =>
          if (l.get != null)
            l.apply().onMessageReceived(new TreeSet[TextMessage]()(TextMessage.Ordering) + message)
          else
            messageListeners(message.sender) -= l)
      }
    })
  }

  /**
   * Registers a listener that is called whenever a new message is sent or received.
   */
  def registerMessageListener(device: Device.ID, listener: OnMessageReceivedListener): Unit = {
    messageListeners(device) += new WeakReference[OnMessageReceivedListener](listener)
    listener.onMessageReceived(MessageStore.getMessages(device, 10))
  }

  /**
   * Returns the unique bluetooth address of the local device.
   */
  def localDeviceId = new Device.ID(bluetoothAdapter.getAddress)

}