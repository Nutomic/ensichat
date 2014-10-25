package com.nutomic.ensichat.activities

import android.bluetooth.BluetoothAdapter
import android.content._
import android.content.pm.ActivityInfo
import android.test.ActivityUnitTestCase
import android.util.Log
import junit.framework.Assert

class MainActivityTest extends ActivityUnitTestCase[MainActivity](classOf[MainActivity]) {

  var lastIntent: Intent = _

  class ActivityContextWrapper(context: Context) extends ContextWrapper(context) {
    override def startService(service: Intent): ComponentName = {
      Log.d("MainActivityTest", "startService")
      lastIntent = service
      null
    }

    override def stopService(name: Intent): Boolean = {
      lastIntent = name
      true
    }

    override def bindService(service: Intent, conn: ServiceConnection, flags: Int): Boolean = false

    override def unbindService(conn: ServiceConnection): Unit = null
  }

  override def setUp(): Unit = {
    setActivityContext(new ActivityContextWrapper(getInstrumentation.getTargetContext))
    startActivity(new Intent(), null, null)
  }

  def testRequestBluetoothDiscoverable(): Unit = {
    val intent: Intent = getStartedActivityIntent
    Assert.assertEquals(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE, intent.getAction)
    Assert.assertEquals(0, intent.getIntExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, -1))
  }

  def testScreenRotate(): Unit = {
    getActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
    getActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
  }

}
