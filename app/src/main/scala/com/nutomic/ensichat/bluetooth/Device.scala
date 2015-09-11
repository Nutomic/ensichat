package com.nutomic.ensichat.bluetooth

import android.bluetooth.BluetoothDevice

private[bluetooth] object Device {

  /**
   * Holds bluetooth device IDs, which are just wrapped addresses (used for type safety).
   *
   * @param id A bluetooth device address.
   */
  case class ID(private val id: String) {

    require(id.matches("([A-Z0-9][A-Z0-9]:){5}[A-Z0-9][A-Z0-9]"), "Invalid device ID format")

    override def toString = id

  }

}

/**
 * Holds information about a remote bluetooth device.
 */
private[bluetooth] case class Device(id: Device.ID, name: String, connected: Boolean,
             btDevice: Option[BluetoothDevice] = None) {

  def this(btDevice: BluetoothDevice, connected: Boolean) = {
    this(new Device.ID(btDevice.getAddress), btDevice.getName, connected, Option(btDevice))
  }

}
