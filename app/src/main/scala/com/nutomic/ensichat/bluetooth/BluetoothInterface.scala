package com.nutomic.ensichat.bluetooth

import java.util.UUID

import android.bluetooth.{BluetoothAdapter, BluetoothDevice, BluetoothSocket}
import android.content.{BroadcastReceiver, Context, Intent, IntentFilter}
import android.os.Handler
import android.preference.PreferenceManager
import android.util.Log
import com.google.common.collect.HashBiMap
import com.nutomic.ensichat.R
import com.nutomic.ensichat.fragments.SettingsFragment
import com.nutomic.ensichat.protocol.ChatService.InterfaceHandler
import com.nutomic.ensichat.protocol._
import com.nutomic.ensichat.protocol.body.ConnectionInfo

import scala.collection.immutable.HashMap

object BluetoothInterface {

  /**
   * Bluetooth service UUID version 5, created with namespace URL and "ensichat.nutomic.com".
   */
  val AppUuid = UUID.fromString("8ed52b7a-4501-5348-b054-3d94d004656e")

}

/**
 * Handles all Bluetooth connectivity.
 */
class BluetoothInterface(context: Context, mainHandler: Handler,
                         onMessageReceived: Message => Unit, callConnectionListeners: () => Unit,
                         onConnectionOpened: (Message) => Boolean)
  extends InterfaceHandler {

  private val Tag = "BluetoothInterface"

  private lazy val btAdapter = BluetoothAdapter.getDefaultAdapter

  private lazy val crypto = new Crypto(context)

  private var devices = new HashMap[Device.ID, Device]()

  private var connections = new HashMap[Device.ID, TransferThread]()

  private lazy val listenThread =
    new ListenThread(context.getString(R.string.app_name), btAdapter, connectionOpened)

  private var cancelDiscovery = false

  private var discovered = Set[Device]()

  private val addressDeviceMap = HashBiMap.create[Address, Device.ID]()

  /**
   * Initializes and starts discovery and listening.
   */
  override def create(): Unit = {
    context.registerReceiver(deviceDiscoveredReceiver,
      new IntentFilter(BluetoothDevice.ACTION_FOUND))
    context.registerReceiver(bluetoothStateReceiver,
      new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))
    context.registerReceiver(discoveryFinishedReceiver,
      new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED))
    // Otherwise, connections are started in [[bluetoothStateReceiver]].
    if (btAdapter.isEnabled)
      startBluetoothConnections()
  }

  /**
   * Stops discovery and listening.
   */
  override def destroy(): Unit = {
    listenThread.cancel()
    cancelDiscovery = true
    try {
      context.unregisterReceiver(deviceDiscoveredReceiver)
      context.unregisterReceiver(bluetoothStateReceiver)
      context.unregisterReceiver(discoveryFinishedReceiver)
    } catch {
      case e: IllegalArgumentException =>
        // This seems to happen for no reason, both on Android 4.4 and 5.0.
        Log.w(Tag, "Failed to unregister receiver", e)
    }
  }

  /**
   * Starts discovery and listening.
   */
  private def startBluetoothConnections(): Unit = {
    listenThread.start()
    cancelDiscovery = false
    discover()
  }

  /**
   * Runs discovery as long as [[cancelDiscovery]] is false.
   */
  def discover(): Unit = {
    if (cancelDiscovery)
      return

    if (!btAdapter.isDiscovering) {
      Log.v(Tag, "Starting discovery")
      btAdapter.startDiscovery()
    }

    val pm = PreferenceManager.getDefaultSharedPreferences(context)
    val scanInterval = pm.getString(SettingsFragment.KeyScanInterval,
      context.getResources.getString(R.string.default_scan_interval)).toInt * 1000
    mainHandler.postDelayed(new Runnable {
      override def run(): Unit = discover()
    }, scanInterval)
  }

  /**
   * Stores newly discovered devices.
   */
  private val deviceDiscoveredReceiver = new BroadcastReceiver() {
    override def onReceive(context: Context, intent: Intent) {
      discovered += new Device(intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE), false)
    }
  }

  /**
   * Initiates connection to discovered devices.
   */
  private val discoveryFinishedReceiver = new BroadcastReceiver() {
    override def onReceive(context: Context, intent: Intent): Unit = {
      discovered.filterNot(d => connections.keySet.contains(d.id))
        .foreach { d =>
          new ConnectThread(d, connectionOpened).start()
          devices += (d.id -> d)
        }
      discovered = Set[Device]()
    }
  }

  /**
   * Starts or stops listening and discovery based on bluetooth state.
   */
  private val bluetoothStateReceiver = new BroadcastReceiver {
    override def onReceive(context: Context, intent: Intent): Unit = {
      intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1) match {
        case BluetoothAdapter.STATE_ON =>
          if (!listenThread.isAlive)
            startBluetoothConnections()
        case BluetoothAdapter.STATE_TURNING_OFF =>
          Log.i(Tag, "Bluetooth disabled, stopping connectivity")
          listenThread.cancel()
          cancelDiscovery = true
          connections.foreach(_._2.close())
        case _ =>
      }
    }
  }


  /**
   * Initiates data transfer with device.
   */
  def connectionOpened(device: Device, socket: BluetoothSocket): Unit = {
    devices += (device.id -> device)
    connections += (device.id ->
      new TransferThread(context, device, socket, this, crypto, onReceiveMessage))
    connections(device.id).start()
  }

  /**
   * Removes device from active connections.
   */
  def onConnectionClosed(device: Device, socket: BluetoothSocket): Unit = {
    devices -= device.id
    connections -= device.id
    callConnectionListeners()
    addressDeviceMap.inverse().remove(device.id)
  }

  /**
   * Passes incoming messages to [[ChatService]].
   *
   * Also uses [[ConnectionInfo]] message to determine mapping from [[Device.ID]] to [[Address]].
   *
   * @param msg The message that was received.
   * @param device Device that sent the message.
   */
  private def onReceiveMessage(msg: Message, device: Device.ID): Unit = msg.body match {
    case info: ConnectionInfo =>
      val address = crypto.calculateAddress(info.key)
      // Service.onConnectionOpened sends message, so mapping already needs to be in place.
      addressDeviceMap.put(address, device)
      if (!onConnectionOpened(msg))
        addressDeviceMap.remove(address)
    case _ =>
      onMessageReceived(msg)
  }

  /**
   * Sends the message to nextHop.
   */
  override def send(nextHop: Address, msg: Message): Unit =
    connections.get(addressDeviceMap.get(nextHop)).foreach(_.send(msg))

  /**
   * Returns all active Bluetooth connections.
   */
  def getConnections: Set[Address] =
    connections.map(x => addressDeviceMap.inverse().get(x._1)).toSet

}
