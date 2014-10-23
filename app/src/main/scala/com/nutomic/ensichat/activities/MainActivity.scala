package com.nutomic.ensichat.activities

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content._
import android.os.Bundle
import android.view.{Menu, MenuItem}
import android.widget.Toast
import com.nutomic.ensichat.R
import com.nutomic.ensichat.bluetooth.ChatService

/**
 * Main activity, holds fragments and requests Bluetooth to be discoverable.
 */
class MainActivity extends Activity {

  private val RequestSetDiscoverable = 1

  /**
   * Initializes layout, starts service and requests Bluetooth to be discoverable.
   */
  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)
    startService(new Intent(this, classOf[ChatService]))

    val intent: Intent = new
        Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
    intent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 0)
    startActivityForResult(intent, RequestSetDiscoverable)
  }

  /**
   * Initializes menu.
   */
  override def onCreateOptionsMenu(menu: Menu): Boolean = {
    getMenuInflater().inflate(R.menu.main, menu)
    return true
  }

  /**
   * Exits with error if bluetooth was not enabled/not set discoverable,
   */
  override def onActivityResult(requestCode: Int, resultCode: Int, data: Intent): Unit = {
    requestCode match {
      case RequestSetDiscoverable =>
        if (resultCode == Activity.RESULT_CANCELED) {
          Toast.makeText(this, R.string.bluetooth_required, Toast.LENGTH_LONG).show()
          finish()
        }
    }
  }

  /**
   * Menu click handler.
   */
  override def onOptionsItemSelected(item: MenuItem): Boolean = {
    item.getItemId match {
      case R.id.exit =>
        stopService(new Intent(this, classOf[ChatService]))
        finish()
        return true
      case _ =>
        return false
    }
  }

}
