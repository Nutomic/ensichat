package com.nutomic.ensichat.bluetooth

import android.bluetooth.BluetoothDevice

private[bluetooth] object Device {

  /**
   * Holds bluetooth device IDs, which are just wrapped addresses (used for type safety).
   *
   * @param Id A bluetooth device address.
   */
  class ID(private val Id: String) {
    require(Id.matches("([A-Z0-9][A-Z0-9]:){5}[A-Z0-9][A-Z0-9]"), "Invalid device ID format")

    override def hashCode = Id.hashCode

    override def equals(a: Any) = a match {
      case o: Device.ID => Id == o.Id
      case _ => false
    }

    override def toString = Id

  }

}

/**
 * Holds information about a remote bluetooth device.
 */
private[bluetooth] class Device(val Id: Device.ID, val Name: String, val Connected: Boolean,
             btDevice: Option[BluetoothDevice] = None) {

  def this(btDevice: BluetoothDevice, connected: Boolean) {
    this(new Device.ID(btDevice.getAddress), btDevice.getName, connected, Option(btDevice))
  }

  def bluetoothDevice = btDevice.get

  override def toString = "Device(Id=" + Id + ", Name=" + Name + ", Connected=" + Connected +
    ", btDevice=" + btDevice + ")"

}
