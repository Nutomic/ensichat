package com.nutomic.ensichat.bluetooth

import java.util.UUID

import android.app.Service
import android.bluetooth.{BluetoothAdapter, BluetoothDevice, BluetoothSocket}
import android.content.{BroadcastReceiver, Context, Intent, IntentFilter}
import android.os.{Handler, IBinder}
import android.util.Log
import android.widget.Toast
import com.nutomic.ensichat.bluetooth.Device.ID
import com.nutomic.ensichat.{Message, R}

import scala.collection.immutable.{HashMap, Set}
import scala.ref.WeakReference

object ChatService {

  /**
   * Bluetooth service UUID version 5, created with namespace URL and "ensichat.nutomic.com".
   */
  val appUuid: UUID = UUID.fromString("8ed52b7a-4501-5348-b054-3d94d004656e")

}

class ChatService extends Service {

  private val Tag = "ChatService"

  private val SCAN_INTERVAL: Int = 5000

  private final val Binder = new ChatServiceBinder(this)

  private var bluetoothAdapter: BluetoothAdapter = _

  private var deviceListener: Set[WeakReference[Map[Device.ID, Device] => Unit]] =
    Set[WeakReference[Map[Device.ID, Device] => Unit]]()

  private var devices: HashMap[Device.ID, Device] = new HashMap[Device.ID, Device]()

  private var connections: HashMap[Device.ID, TransferThread] =
    new HashMap[Device.ID, TransferThread]()

  private var ListenThread: ListenThread = _

  private var isDestroyed = false

  private val MainHandler: Handler = new Handler()

  /**
   * Initializes BroadcastReceiver for discovery, starts discovery and listens for connections.
   */
  override def onCreate(): Unit = {
    super.onCreate()

    bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

    var filter: IntentFilter = new IntentFilter(BluetoothDevice.ACTION_FOUND)
    registerReceiver(mReceiver, filter)
    filter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
    registerReceiver(mReceiver, filter)
    Log.i(Tag, "Discovery started")
    discover()
    ListenThread = new ListenThread(getString(R.string.app_name), bluetoothAdapter, onConnected)
    ListenThread.start()
  }

  override def onStartCommand(intent: Intent, flags: Int, startId: Int): Int = {
    return Service.START_STICKY
  }

  override def onBind(intent: Intent): IBinder = {
    return Binder
  }

  override def onDestroy(): Unit = {
    super.onDestroy()
    ListenThread.cancel()
    isDestroyed = true
    unregisterReceiver(mReceiver)
  }

  /**
   * Stops any current discovery, then starts a new one, recursively until service is stopped.
   */
  def discover(): Unit = {
    if (isDestroyed)
      return

    if (!bluetoothAdapter.isDiscovering()) {
      Log.v(Tag, "Running discovery")
      bluetoothAdapter.startDiscovery()
    }

    MainHandler.postDelayed(new Runnable {
      override def run(): Unit = discover()
    }, SCAN_INTERVAL)
  }

  /**
   * Receives newly discovered devices and connects to them.
   */
  private final def mReceiver: BroadcastReceiver  = new BroadcastReceiver() {
    override def onReceive(context: Context, intent: Intent) {
      intent.getAction() match {
        case BluetoothDevice.ACTION_FOUND =>
          val device: Device =
            new Device(intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE), false)
          devices = devices + (device.id -> device)
          new ConnectThread(device, onConnected).start()
        case _ =>
      }
    }
  }

  /**
   * Registers a listener that is called whenever a new device is connected.
   */
  def registerDeviceListener(listener: Map[Device.ID, Device] => Unit): Unit = {
    deviceListener = deviceListener + new WeakReference[(Map[ID, Device]) => Unit](listener)
    listener(devices)
  }

  /**
   * Unregisters a device listener.
   */
  def unregisterDeviceListener(listener: Map[Device.ID, Device] => Unit): Unit = {
    deviceListener.foreach(l =>
      if (l == listener)
        deviceListener = deviceListener - l)
  }

  def onConnected(device: Device, socket: BluetoothSocket): Unit = {
    val updatedDevice: Device = new Device(device.bluetoothDevice, true)
    devices = devices + (device.id -> updatedDevice)
    connections = connections + (device.id -> new TransferThread(updatedDevice, socket, onReceive))
    connections(device.id).start()
    deviceListener.foreach(d =>
      if (d != null)
        d.apply()(devices)
      else
        deviceListener = deviceListener - d)
  }

  def send(device: Device.ID, message: Message): Unit = {
    connections.apply(device).send(message)
  }

  def onReceive(device: Device.ID, message: Message): Unit = {
    MainHandler.post(new Runnable {
      override def run(): Unit =
        Toast.makeText(ChatService.this, devices(device).name + " sent: " + message, Toast.LENGTH_SHORT)
          .show()
    })
  }

}