package com.nutomic.ensichat.bluetooth

import android.app.Service
import android.bluetooth.{BluetoothDevice, BluetoothAdapter}
import android.content.{Context, BroadcastReceiver, IntentFilter, Intent}
import android.os.IBinder
import android.util.Log
import com.nutomic.ensichat.bluetooth.ChatService.DeviceListener

object ChatService {
  trait DeviceListener {
    def onDeviceConnected(device: Device): Unit
  }
}

class ChatService extends Service {

  private val TAG = "ChatService"

  private final val mBinder = new ChatServiceBinder(this)

  private var mBluetoothAdapter: BluetoothAdapter = _

  private var mDeviceListener: DeviceListener = _

  override def onCreate(): Unit = {
    super.onCreate()

    mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

    var filter: IntentFilter = new IntentFilter(BluetoothDevice.ACTION_FOUND)
    registerReceiver(mReceiver, filter)
    filter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
    registerReceiver(mReceiver, filter)
    doDiscovery()
  }

  override def onBind(intent: Intent): IBinder = {
    return mBinder
  }

  def doDiscovery() {
    // If we're already discovering, stop it.
    if (mBluetoothAdapter.isDiscovering()) {
      mBluetoothAdapter.cancelDiscovery()
    }

    mBluetoothAdapter.startDiscovery()
    Log.i(TAG, "Discovery started")
  }

  private final def mReceiver: BroadcastReceiver  = new BroadcastReceiver() {
    override def onReceive(context: Context, intent: Intent) {
      intent.getAction() match {
        case BluetoothDevice.ACTION_FOUND =>
          val btDevice: BluetoothDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
          mDeviceListener.onDeviceConnected(new Device(btDevice))
        case _ =>
      }
    }
  }

  def registerDeviceListener(listener: DeviceListener): Unit = {
    mDeviceListener = listener
  }

}