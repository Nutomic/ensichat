package com.nutomic.ensichat.fragments

import android.app.ListFragment
import android.content.Intent
import android.os.Bundle
import android.view._
import android.widget.ListView
import com.nutomic.ensichat.R
import com.nutomic.ensichat.activities.{AddContactsActivity, EnsiChatActivity, MainActivity, SettingsActivity}
import com.nutomic.ensichat.protocol.ChatService
import com.nutomic.ensichat.util.Database.OnContactsUpdatedListener
import com.nutomic.ensichat.util.{Database, UsersAdapter}

/**
 * Lists all nearby, connected devices.
 */
class ContactsFragment extends ListFragment with OnContactsUpdatedListener {

  private lazy val adapter = new UsersAdapter(getActivity)

  private lazy val database = new Database(getActivity)

  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)

    setListAdapter(adapter)
    setHasOptionsMenu(true)

    getActivity.asInstanceOf[EnsiChatActivity].runOnServiceConnected(() => {
      database.getContacts.foreach(adapter.add)
      database.runOnContactsUpdated(this)
    })
  }

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup,
                            savedInstanceState: Bundle): View =
    inflater.inflate(R.layout.fragment_contacts, container, false)

  override def onCreateOptionsMenu(menu: Menu, inflater: MenuInflater): Unit = {
    super.onCreateOptionsMenu(menu, inflater)
    inflater.inflate(R.menu.main, menu)
  }

  override def onOptionsItemSelected(item: MenuItem): Boolean = item.getItemId match {
    case R.id.add_contact =>
      startActivity(new Intent(getActivity, classOf[AddContactsActivity]))
      true
    case R.id.settings =>
      startActivity(new Intent(getActivity, classOf[SettingsActivity]))
      true
    case R.id.exit =>
      getActivity.stopService(new Intent(getActivity, classOf[ChatService]))
      getActivity.finish()
      true
    case _ =>
      super.onOptionsItemSelected(item)
  }

  /**
   * Opens a chat with the clicked device.
   */
  override def onListItemClick(l: ListView, v: View, position: Int, id: Long): Unit =
    getActivity.asInstanceOf[MainActivity].openChat(adapter.getItem(position).address)

  override def onContactsUpdated(): Unit = {
    getActivity.runOnUiThread(new Runnable {
      override def run(): Unit = {
        adapter.clear()
        database.getContacts.foreach(adapter.add)
      }
    })
  }
}
