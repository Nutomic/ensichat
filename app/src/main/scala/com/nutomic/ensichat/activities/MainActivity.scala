package com.nutomic.ensichat.activities

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content._
import android.os.Bundle
import android.view.{MenuItem, Menu}
import android.widget.Toast
import com.nutomic.ensichat.R
import com.nutomic.ensichat.bluetooth.ChatService

class MainActivity extends Activity {

  private final val REQUEST_ENABLE_BLUETOOTH = 1

  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)
    startService(new Intent(this, classOf[ChatService]))
    val intent: Intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
    startActivityForResult(intent, REQUEST_ENABLE_BLUETOOTH)
  }

  override def onCreateOptionsMenu(menu: Menu): Boolean = {
    getMenuInflater().inflate(R.menu.main, menu)
    return true
  }

  override def onActivityResult(requestCode: Int, resultCode: Int, data: Intent): Unit = {
    requestCode match {
      case REQUEST_ENABLE_BLUETOOTH =>
        if (resultCode != Activity.RESULT_OK) {
          Toast.makeText(this, R.string.bluetooth_required, Toast.LENGTH_LONG).show()
          finish()
        }
    }
  }

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
