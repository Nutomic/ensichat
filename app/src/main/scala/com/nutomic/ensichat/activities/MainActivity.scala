package com.nutomic.ensichat.activities

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content._
import android.os.Bundle
import android.view.{Menu, MenuItem}
import android.widget.Toast
import com.nutomic.ensichat.R
import com.nutomic.ensichat.bluetooth.{ChatService, Device}
import com.nutomic.ensichat.fragments.{ChatFragment, ContactsFragment}

import scala.collection.mutable.HashMap

/**
 * Main activity, entry point for app start.
 */
class MainActivity extends Activity {

  private val RequestSetDiscoverable = 1

  private var ContactsFragment: ContactsFragment = _

  private val ChatFragments = new HashMap[Device.ID, ChatFragment]()

  private var currentChat: Option[Device.ID] = None

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
    val fm = getFragmentManager
    if (savedInstanceState != null) {
      ContactsFragment = fm.getFragment(savedInstanceState, classOf[ContactsFragment].getName)
        .asInstanceOf[ContactsFragment]
      for (i <- 0 until savedInstanceState.getInt("chat_fragments_count", 0)) {
        val key = classOf[ChatFragment].getName + i
        val cf = fm.getFragment(savedInstanceState, key).asInstanceOf[ChatFragment]
        ChatFragments += (cf.getDevice -> cf)
      }
      currentChat = Some(new Device.ID(savedInstanceState.getString("current_chat")))
      currentChat.collect{case c => openChat(c) }
    } else {
      ContactsFragment = new ContactsFragment()
    }
    fm.beginTransaction()
      .add(android.R.id.content, ContactsFragment)
      .commit()
  }

  /**
   * Saves all fragment state.
   */
  override def onSaveInstanceState(outState: Bundle): Unit = {
    super.onSaveInstanceState(outState)
    getFragmentManager.putFragment(outState, classOf[ContactsFragment].getName, ContactsFragment)
    outState.putInt("chat_fragments_count", ChatFragments.size)
    var i: Int = 0
    ChatFragments.foreach(cf => {
      getFragmentManager.putFragment(outState, classOf[ChatFragment].getName + i, cf._2)
      i += 1
    })
    outState.putString("current_chat", currentChat.toString)
  }

  override def onCreateOptionsMenu(menu: Menu): Boolean = {
    getMenuInflater.inflate(R.menu.main, menu)
    true
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

  override def onOptionsItemSelected(item: MenuItem): Boolean = {
    item.getItemId match {
      case R.id.exit =>
        stopService(new Intent(this, classOf[ChatService]))
        finish()
        true
      case _ =>
        false
    }
  }

  /**
   * Opens a chat fragment for the given device, creating the fragment if needed.
   */
  def openChat(device: Device.ID): Unit = {
    currentChat = Some(device)
    val ft = getFragmentManager.beginTransaction()
    if (!ChatFragments.keySet.contains(device)) {
      ChatFragments += (device -> new ChatFragment(device))
      ft.add(android.R.id.content, ChatFragments.apply(device))
    }
    ft.detach(ContactsFragment)
      .attach(ChatFragments.apply(device))
      .commit()
  }

  /**
   * If in a ChatFragment, goes back up to ContactsFragment.
   */
  override def onBackPressed(): Unit = {
    if (currentChat != None) {
      getFragmentManager
        .beginTransaction()
        .detach(ChatFragments.apply(currentChat.get))
        .attach(ContactsFragment)
        .commit()
      currentChat = None
    } else
      super.onBackPressed()
  }

}
