package com.nutomic.ensichat.activities

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content._
import android.os.Bundle
import android.view.MenuItem
import android.widget.Toast
import com.nutomic.ensichat.R
import com.nutomic.ensichat.fragments.{ChatFragment, ContactsFragment}
import com.nutomic.ensichat.protocol.Address

object MainActivity {

  val ActionOpenChat = "open_chat"

  val ExtraAddress = "address"
}

/**
 * Main activity, entry point for app start.
 */
class MainActivity extends EnsiChatActivity {

  private val RequestSetDiscoverable = 1

  private var ContactsFragment: ContactsFragment = _

  private var currentChat: Option[Address] = None

  /**
   * Initializes layout, starts service and requests Bluetooth to be discoverable.
   */
  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)

    val intent: Intent = new
        Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
    intent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 0)
    startActivityForResult(intent, RequestSetDiscoverable)

    val fm = getFragmentManager
    if (savedInstanceState != null) {
      ContactsFragment = fm.getFragment(savedInstanceState, classOf[ContactsFragment].getName)
        .asInstanceOf[ContactsFragment]
      if (savedInstanceState.containsKey("current_chat")) {
        currentChat = Option(new Address(savedInstanceState.getByteArray("current_chat")))
        openChat(currentChat.get)
      }
    } else {
      ContactsFragment = new ContactsFragment()
      fm.beginTransaction()
        .add(android.R.id.content, ContactsFragment)
        .commit()
    }

    if (getIntent.getAction == MainActivity.ActionOpenChat)
      openChat(new Address(getIntent.getStringExtra(MainActivity.ExtraAddress)))
  }

  /**
   * Saves all fragment state.
   */
  override def onSaveInstanceState(outState: Bundle): Unit = {
    super.onSaveInstanceState(outState)
    getFragmentManager.putFragment(outState, classOf[ContactsFragment].getName, ContactsFragment)
    currentChat.collect{case c => outState.putByteArray("current_chat", c.Bytes)}
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
   * Opens a chat fragment for the given device, creating the fragment if needed.
   */
  def openChat(address: Address): Unit = {
    currentChat = Some(address)
    getFragmentManager
      .beginTransaction()
      .detach(ContactsFragment)
      .add(android.R.id.content, new ChatFragment(address))
      .commit()
    getActionBar.setDisplayHomeAsUpEnabled(true)
  }

  /**
   * If in a ChatFragment, goes back up to ContactsFragment.
   */
  override def onBackPressed(): Unit = {
    if (currentChat != None) {
      getFragmentManager
        .beginTransaction()
        .remove(getFragmentManager.findFragmentById(android.R.id.content))
        .attach(ContactsFragment)
        .commit()
      currentChat = None
      getActionBar.setDisplayHomeAsUpEnabled(false)
      setTitle(R.string.app_name)
    } else
      super.onBackPressed()
  }

  override def onOptionsItemSelected(item: MenuItem): Boolean = item.getItemId match {
    case android.R.id.home =>
      onBackPressed()
      true;
    case _ =>
      super.onOptionsItemSelected(item);
  }

}
