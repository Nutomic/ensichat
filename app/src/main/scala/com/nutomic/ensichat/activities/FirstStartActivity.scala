package com.nutomic.ensichat.activities

import android.bluetooth.BluetoothAdapter
import android.content.{Context, Intent}
import android.os.Bundle
import android.preference.PreferenceManager
import android.support.v7.app.AppCompatActivity
import android.view.View.OnClickListener
import android.view.inputmethod.{EditorInfo, InputMethodManager}
import android.view.{KeyEvent, View}
import android.widget.TextView.OnEditorActionListener
import android.widget.{Button, EditText, TextView}
import com.nutomic.ensichat.R
import com.nutomic.ensichat.fragments.SettingsFragment

/**
 * Shown on first start, lets the user enter their name.
 */
class FirstStartActivity extends AppCompatActivity with OnEditorActionListener with OnClickListener {

  private val KeyIsFirstStart = "first_start"

  private lazy val preferences = PreferenceManager.getDefaultSharedPreferences(this)
  private lazy val imm = getSystemService(Context.INPUT_METHOD_SERVICE)
    .asInstanceOf[InputMethodManager]

  private lazy val username = findViewById(R.id.username).asInstanceOf[EditText]
  private lazy val done     = findViewById(R.id.done)    .asInstanceOf[Button]

  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)

    if (!preferences.getBoolean(KeyIsFirstStart, true)) {
      startMainActivity()
      return
    }

    setContentView(R.layout.activity_first_start)
    setTitle(R.string.welcome)

    username.setText(BluetoothAdapter.getDefaultAdapter.getName.trim)
    username.setOnEditorActionListener(this)
    done.setOnClickListener(this)

    imm.showSoftInput(username, InputMethodManager.SHOW_IMPLICIT)
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
   * Saves values and calls [[startMainActivity]].
   */
  private def save(): Unit = {
    imm.hideSoftInputFromWindow(username.getWindowToken, 0)

    preferences
      .edit()
      .putBoolean(KeyIsFirstStart, false)
      .putString(SettingsFragment.KeyUserName, username.getText.toString.trim)
      .putString(SettingsFragment.KeyUserStatus, getString(R.string.default_user_status))
      .apply()

    startMainActivity()
  }

  def startMainActivity(): Unit = {
    val intent = new Intent(this, classOf[MainActivity])
    intent.setAction(MainActivity.ActionRequestBluetooth)
    startActivity(intent)
    finish()
  }

}