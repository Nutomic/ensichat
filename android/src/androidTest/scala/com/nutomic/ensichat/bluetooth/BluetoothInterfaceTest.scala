package com.nutomic.ensichat.bluetooth

import android.bluetooth.BluetoothAdapter
import android.os.Handler
import android.test.AndroidTestCase

class BluetoothInterfaceTest extends AndroidTestCase {

  private lazy val btInterface = new BluetoothInterface(getContext, new Handler(), null)

  /**
   * Test for issue [[https://github.com/Nutomic/ensichat/issues/3 #3]].
   */
  def testStartBluetoothOff(): Unit = {
    val btAdapter = BluetoothAdapter.getDefaultAdapter
    if (btAdapter == null)
      return

    btAdapter.disable()
    btInterface.create()
  }

}
