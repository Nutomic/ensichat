package com.nutomic.ensichat.bluetooth

import android.bluetooth.BluetoothDevice

class Device(mBluetoothDevice: BluetoothDevice) {

  def name = mBluetoothDevice.getName()

}