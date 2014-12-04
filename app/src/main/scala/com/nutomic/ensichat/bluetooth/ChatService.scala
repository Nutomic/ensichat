package com.nutomic.ensichat.bluetooth

import java.util.{Date, UUID}

import android.app.Service
import android.bluetooth.{BluetoothAdapter, BluetoothDevice, BluetoothSocket}
import android.content.{BroadcastReceiver, Context, Intent, IntentFilter}
import android.os.Handler
import android.preference.PreferenceManager
import android.util.Log
import com.google.common.collect.HashBiMap
import com.nutomic.ensichat.R
import com.nutomic.ensichat.aodvv2._
import com.nutomic.ensichat.bluetooth.ChatService.{OnMessageReceivedListener, OnNearbyContactsChangedListener}
import com.nutomic.ensichat.messages._
import com.nutomic.ensichat.util.Database
import org.msgpack.ScalaMessagePack

import scala.collection.SortedSet
import scala.collection.immutable.{HashMap, HashSet, TreeSet}
import scala.ref.WeakReference

object ChatService {

  /**
   * Bluetooth service UUID version 5, created with namespace URL and "ensichat.nutomic.com".
   */
  val appUuid: UUID = UUID.fromString("8ed52b7a-4501-5348-b054-3d94d004656e")

  val KEY_GENERATION_FINISHED = "com.nutomic.ensichat.messages.KEY_GENERATION_FINISHED"

  /**
   * Used with [[ChatService.registerConnectionListener]], called when a bluetooth device
   * connects or disconnects
   */
  trait OnNearbyContactsChangedListener {
    def onNearbyContactsChanged(devices: Set[Address]): Unit
  }

  trait OnMessageReceivedListener {
    def onMessageReceived(messages: SortedSet[Message]): Unit
  }

}

/**
 * Handles all Bluetooth connectivity.
 */
class ChatService extends Service {

  private val Tag = "ChatService"

  private val Binder = new ChatServiceBinder(this)

  private var bluetoothAdapter: BluetoothAdapter = _

  /**
   * For this (and [[messageListeners]], functions would be useful instead of instances,
   * but on a Nexus S (Android 4.1.2), these functions are garbage collected even when
   * referenced.
   */
  private var connectionListeners = new HashSet[WeakReference[OnNearbyContactsChangedListener]]()

  private var messageListeners = Set[WeakReference[OnMessageReceivedListener]]()

  private var devices = new HashMap[Device.ID, Device]()

  private var connections = new HashMap[Device.ID, TransferThread]()

  private var ListenThread: ListenThread = _

  private var cancelDiscovery = false

  private val MainHandler = new Handler()

  private lazy val Database = new Database(this)

  private lazy val Crypto = new Crypto(this)

  private val AddressDeviceMap = HashBiMap.create[Address, Device.ID]()

  /**
   * Initializes BroadcastReceiver for discovery, starts discovery and listens for connections.
   */
  override def onCreate(): Unit = {
    super.onCreate()

    bluetoothAdapter = BluetoothAdapter.getDefaultAdapter

    registerReceiver(DeviceDiscoveredReceiver, new IntentFilter(BluetoothDevice.ACTION_FOUND))
    registerReceiver(BluetoothStateReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))
    if (bluetoothAdapter.isEnabled) {
      startBluetoothConnections()
    }

    if (!Crypto.localKeysExist) {
      new Thread(new Runnable {
        override def run(): Unit = {
          Crypto.generateLocalKeys()
        }
      }).start()
    } else
      Log.i(Tag, "Service started, address is " + Crypto.getLocalAddress)
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

    val scanInterval = PreferenceManager.getDefaultSharedPreferences(this)
      .getString("scan_interval_seconds", "5").toInt * 1000
    MainHandler.postDelayed(new Runnable {
      override def run(): Unit = discover()
    }, scanInterval)
  }

  /**
   * Receives newly discovered devices and connects to them.
   */
  private val DeviceDiscoveredReceiver = new BroadcastReceiver() {
    override def onReceive(context: Context, intent: Intent) {
      val device: Device =
        new Device(intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE), false)
      devices += (device.Id -> device)
      new ConnectThread(device, onConnectionChanged).start()
    }
  }

  /**
   * Starts or stops listening and discovery based on bluetooth state.
   */
  private val BluetoothStateReceiver = new BroadcastReceiver {
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
    ListenThread =
      new ListenThread(getString(R.string.app_name), bluetoothAdapter, onConnectionChanged)
    ListenThread.start()
  }

  /**
   * Registers a listener that is called whenever a new device is connected.
   */
  def registerConnectionListener(listener: OnNearbyContactsChangedListener): Unit = {
    connectionListeners += new WeakReference[OnNearbyContactsChangedListener](listener)
    nearbyContactsChanged(listener)
  }

  /**
   * Called when a Bluetooth device is connected.
   *
   * Adds the device to [[connections]], notifies all [[connectionListeners]], sends DeviceInfoMessage.
   *
   * @param device The updated device info for the remote device.
   * @param socket A socket for data transfer if device.connected is true, otherwise null.
   */
  def onConnectionChanged(device: Device, socket: BluetoothSocket): Unit = {
    devices += (device.Id -> device)

    if (device.Connected) {
      connections += (device.Id ->
        new TransferThread(device, socket, this, Crypto, onReceiveMessage))
      connections(device.Id).start()
    } else {
      Log.i(Tag, device + " has disconnected")
      AddressDeviceMap.inverse().remove(device.Id)
    }
  }

  /**
   * Calls listener with [[devices]] (converting [[Device.ID]]s to [[Address]]es.
   */
  def nearbyContactsChanged(listener: OnNearbyContactsChangedListener) = {
    listener.onNearbyContactsChanged(
      devices.keySet.map(d => AddressDeviceMap.inverse().get(d)).filter(_ != null))
  }

  /**
   * Sends message to the device specified as receiver,
   */
  def send(message: Message): Unit = {
    assert(message.sender == Crypto.getLocalAddress, "Message must be sent from local device")

    if (!AddressDeviceMap.containsKey(message.receiver)) {
      Log.w(Tag, "Receiver " + message.receiver + " is not connected, ignoring message")
      return
    }

    val header = new MessageHeader(Data.Type, MessageHeader.DefaultHopLimit,
      new Date(), Crypto.getLocalAddress, message.receiver, 0, 0)

    val plain = message.write(Crypto.calculateSignature(message))
    val (encrypted, key) = Crypto.encrypt(message.receiver, plain)
    val packer = new ScalaMessagePack().createBufferPacker()
    packer
      .write(encrypted)
      .write(key)
    val body = new Data(packer.toByteArray)

    connections.apply(AddressDeviceMap.get(message.receiver)).send(header, body)
    Database.addMessage(message)
  }

  /**
   * Saves the message to database and sends it to registered listeners.
   *
   * If you want to send a new message, use [[send]].
   *
   * Messages must always be sent between local device and a contact.
   *
   * NOTE: Messages sent from the local node using [[send]] are also passed through this method.
   */
  private def onReceiveMessage(header: MessageHeader, body: MessageBody, device: Device.ID): Unit = {
    assert(header.Origin != Crypto.getLocalAddress)

    body match {
      case info: ConnectionInfo =>
        if (header.Origin == Crypto.getLocalAddress)
          return
        onNeighborConnected(info, device)
      case data: Data =>
        val up = new ScalaMessagePack().createBufferUnpacker(data.data)
        val encrypted = up.readByteArray()
        val key = up.readByteArray()
        val (message, signature) = Message.read(Crypto.decrypt(encrypted, key))

        if (!Crypto.isValidSignature(message, signature)) {
          Log.i(Tag, "Dropping message with invalid signature from " + header.Origin)
          return
        }

        Database.addMessage(message)
        MainHandler.post(new Runnable {
          override def run(): Unit = {
            messageListeners.foreach(l =>
              if (l.get != null)
                l.apply().onMessageReceived(new TreeSet[Message]()(Message.Ordering) + message)
              else
                messageListeners -= l)
          }
        })
    }
  }

  /**
   * Called when a [[ConnectionInfo]] message from a new neighbor is received.
   */
  private def onNeighborConnected(info: ConnectionInfo, device: Device.ID): Unit = {
    val sender = Crypto.calculateAddress(info.key)
    if (sender == Address.Broadcast || sender == Address.Null) {
      connections(device).close()
      return
    }

    if (!Crypto.havePublicKey(sender)) {
      Crypto.addPublicKey(sender, info.key)
      Log.i(Tag, "Added public key for new device " + sender.toString)
    }

    AddressDeviceMap.put(sender, device)
    Log.i(Tag, "Node " + sender + " connected as " + device)

    connectionListeners.foreach(l => l.get match {
      case Some(c) => nearbyContactsChanged(c)
      case None => connectionListeners -= l
    })

  }

  /**
   * Registers a listener that is called whenever a new message is sent or received.
   */
  def registerMessageListener(listener: OnMessageReceivedListener): Unit = {
    messageListeners += new WeakReference[OnMessageReceivedListener](listener)
  }

  /**
   * Returns the unique bluetooth address of the local device.
   */
  private def localDeviceId = new Device.ID(bluetoothAdapter.getAddress)

  def database = Database

}
