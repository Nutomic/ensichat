package com.nutomic.ensichat.activities

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.pm.PackageManager
import android.content.{Context, Intent}
import android.os.Bundle
import android.preference.PreferenceManager
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.view.View.OnClickListener
import android.view.inputmethod.{EditorInfo, InputMethodManager}
import android.view.{KeyEvent, View}
import android.widget.TextView.OnEditorActionListener
import android.widget.{Button, EditText, TextView, Toast}
import com.nutomic.ensichat.R
import com.nutomic.ensichat.core.interfaces.SettingsInterface
import com.nutomic.ensichat.core.interfaces.SettingsInterface._

/**
 * Shown on first start, lets the user enter their name.
 */
class FirstStartActivity extends AppCompatActivity with OnEditorActionListener with OnClickListener {

  private val KeyIsFirstStart = "first_start"
  private val RequestLocationPermission = 127

  private lazy val preferences = PreferenceManager.getDefaultSharedPreferences(this)
  private lazy val imm = getSystemService(Context.INPUT_METHOD_SERVICE)
    .asInstanceOf[InputMethodManager]

  private lazy val username = findViewById(R.id.username).asInstanceOf[EditText]
  private lazy val done     = findViewById(R.id.done)    .asInstanceOf[Button]

  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)

    setContentView(R.layout.activity_first_start)
    setTitle(R.string.welcome)

    username.setText(BluetoothAdapter.getDefaultAdapter.getName.trim)
    username.setOnEditorActionListener(this)
    done.setOnClickListener(this)

    val permission = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
    if (preferences.getBoolean(KeyIsFirstStart, true)) {
      imm.showSoftInput(username, InputMethodManager.SHOW_IMPLICIT)
    }
    else if (permission != PackageManager.PERMISSION_GRANTED) {
      requestLocationPermission()
    }
    else {
      startMainActivity()
    }
  }

  /**
   * Calls [[save]] on enter click.
   */
  override def onEditorAction(v: TextView, actionId: Int, event: KeyEvent): Boolean = {
    if (actionId == EditorInfo.IME_ACTION_DONE) {
      save()
      true
    }
    else
      false
  }

  override def onClick(v: View): Unit = save()

  /**
   * Saves username and default settings values, then calls [[startMainActivity]].
   */
  private def save(): Unit = {
    imm.hideSoftInputFromWindow(username.getWindowToken, 0)

    preferences
      .edit()
      .putBoolean(KeyIsFirstStart, false)
      .putString(KeyUserName,               username.getText.toString.trim)
      .putString(KeyUserStatus,             SettingsInterface.DefaultUserStatus)
      .putBoolean(KeyNotificationSoundsOn,  DefaultNotificationSoundsOn)
      .putString(KeyScanInterval,           DefaultScanInterval.toString)
      .putString(KeyAddresses,              DefaultAddresses)
      .apply()

    requestLocationPermission()
  }

  private def requestLocationPermission(): Unit =
    ActivityCompat.requestPermissions(this, Array(Manifest.permission.ACCESS_COARSE_LOCATION), RequestLocationPermission)

  override def onRequestPermissionsResult(requestCode: Int,
                                          permissions: Array[String], grantResults: Array[Int]): Unit = requestCode match {
    case RequestLocationPermission =>
      if (grantResults.length > 0 && grantResults(0) == PackageManager.PERMISSION_GRANTED) {
        startMainActivity()
      } else {
        Toast.makeText(this, R.string.toast_location_required, Toast.LENGTH_SHORT).show()
      }
  }

  def startMainActivity(): Unit = {
    val intent = new Intent(this, classOf[MainActivity])
    intent.setAction(MainActivity.ActionRequestBluetooth)
    startActivity(intent)
    finish()
  }

}