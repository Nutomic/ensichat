package com.nutomic.ensichat.bluetooth

import android.bluetooth.BluetoothAdapter
import android.os.Handler
import android.test.AndroidTestCase

class BluetoothInterfaceTest extends AndroidTestCase {

  private lazy val adapter = new BluetoothInterface(getContext, new Handler(), Message => Unit, 
    () => Unit, Message => false)

  /**
   * Test for issue [[https://github.com/Nutomic/ensichat/issues/3 #3]].
   */
  def testStartBluetoothOff(): Unit = {
    BluetoothAdapter.getDefaultAdapter.disable()
    adapter.create()
  }

}
