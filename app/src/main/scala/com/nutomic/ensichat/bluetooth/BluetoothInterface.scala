package com.nutomic.ensichat.bluetooth

import java.util.UUID

import android.bluetooth.{BluetoothAdapter, BluetoothDevice, BluetoothSocket}
import android.content.{BroadcastReceiver, Context, Intent, IntentFilter}
import android.preference.PreferenceManager
import android.util.Log
import com.google.common.collect.HashBiMap
import com.nutomic.ensichat.R
import com.nutomic.ensichat.protocol.ChatService.InterfaceHandler
import com.nutomic.ensichat.protocol._
import com.nutomic.ensichat.protocol.messages.{ConnectionInfo, Message}

import scala.collection.immutable.HashMap

object BluetoothInterface {

  /**
   * Bluetooth service UUID version 5, created with namespace URL and "ensichat.nutomic.com".
   */
  val AppUuid: UUID = UUID.fromString("8ed52b7a-4501-5348-b054-3d94d004656e")

}

/**
 * Handles all Bluetooth connectivity.
 */
class BluetoothInterface(service: ChatService, crypto: Crypto) extends InterfaceHandler {

  private val Tag = "BluetoothInterface"

  private lazy val btAdapter = BluetoothAdapter.getDefaultAdapter

  private var devices = new HashMap[Device.ID, Device]()

  private var connections = new HashMap[Device.ID, TransferThread]()

  private lazy val listenThread =
    new ListenThread(service.getString(R.string.app_name), btAdapter, onConnectionOpened)

  private var cancelDiscovery = false

  private var discovered = Set[Device]()

  private val addressDeviceMap = HashBiMap.create[Address, Device.ID]()

  /**
   * Initializes and starts discovery and listening.
   */
  override def create(): Unit = {
    service.registerReceiver(DeviceDiscoveredReceiver,
      new IntentFilter(BluetoothDevice.ACTION_FOUND))
    service.registerReceiver(BluetoothStateReceiver,
      new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))
    service.registerReceiver(DiscoveryFinishedReceiver,
      new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED))
    startBluetoothConnections()
  }

  /**
   * Stops discovery and listening.
   */
  override def destroy(): Unit = {
    listenThread.cancel()
    cancelDiscovery = true
    service.unregisterReceiver(DeviceDiscoveredReceiver)
    service.unregisterReceiver(BluetoothStateReceiver)
    service.unregisterReceiver(DiscoveryFinishedReceiver)
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

    val scanInterval = PreferenceManager.getDefaultSharedPreferences(service)
      .getString("scan_interval_seconds", "15").toInt * 1000
    service.MainHandler.postDelayed(new Runnable {
      override def run(): Unit = discover()
    }, scanInterval)
  }

  /**
   * Stores newly discovered devices.
   */
  private val DeviceDiscoveredReceiver = new BroadcastReceiver() {
    override def onReceive(context: Context, intent: Intent) {
      discovered += new Device(intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE), false)
    }
  }

  /**
   * Initiates connection to discovered devices.
   */
  private val DiscoveryFinishedReceiver = new BroadcastReceiver() {
    override def onReceive(context: Context, intent: Intent): Unit = {
      discovered.filterNot(d => connections.keySet.contains(d.id))
        .foreach { d =>
          new ConnectThread(d, onConnectionOpened).start()
          devices += (d.id -> d)
        }
      discovered = Set[Device]()
    }
  }

  /**
   * Starts or stops listening and discovery based on bluetooth state.
   */
  private val BluetoothStateReceiver = new BroadcastReceiver {
    override def onReceive(context: Context, intent: Intent): Unit = {
      intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1) match {
        case BluetoothAdapter.STATE_ON =>
          if (crypto.localKeysExist)
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
  def onConnectionOpened(device: Device, socket: BluetoothSocket): Unit = {
    devices += (device.id -> device)
    connections += (device.id ->
      new TransferThread(device, socket, this, crypto, onReceiveMessage))
    connections(device.id).start()
  }

  /**
   * Removes device from active connections.
   */
  def onConnectionClosed(device: Device, socket: BluetoothSocket): Unit = {
    devices -= device.id
    connections -= device.id
    service.callConnectionListeners()
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
  private def onReceiveMessage(msg: Message, device: Device.ID): Unit = msg.Body match {
    case info: ConnectionInfo =>
      val address = crypto.calculateAddress(info.key)
      // Service.onConnectionOpened sends message, so mapping already needs to be in place.
      addressDeviceMap.put(address, device)
      if (!service.onConnectionOpened(msg))
        addressDeviceMap.remove(address)
    case _ =>
      service.onMessageReceived(msg)
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
