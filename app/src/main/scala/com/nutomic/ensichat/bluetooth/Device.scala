package com.nutomic.ensichat.bluetooth

import android.bluetooth.BluetoothDevice

object Device {

  /**
   * Holds bluetooth device IDs, which are just wrapped addresses (used for type safety).
   *
   * @param Id A bluetooth device address.
   */
  class ID(private val Id: String) {
    override def hashCode = Id.hashCode
    override def equals(a: Any) = a match {
      case other: Device.ID => Id == other.Id
      case _ => false
    }
  }

}

/**
 * Holds information about a remote bluetooth device.
 */
class Device(BluetoothDevice: BluetoothDevice, Connected: Boolean) {

  def id = new Device.ID(bluetoothDevice.getAddress)

  def name = BluetoothDevice.getName

  def connected = Connected

  def bluetoothDevice = BluetoothDevice

}
