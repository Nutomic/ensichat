package com.nutomic.ensichat.activities

import android.app.AlertDialog.Builder
import android.content.DialogInterface.OnClickListener
import android.content._
import android.os.Bundle
import android.support.v4.app.NavUtils
import android.support.v4.content.LocalBroadcastManager
import android.view._
import android.widget.AdapterView.OnItemClickListener
import android.widget._
import com.nutomic.ensichat.R
import com.nutomic.ensichat.service.CallbackHandler
import com.nutomic.ensichat.util.Database
import com.nutomic.ensichat.views.UsersAdapter

/**
 * Lists all nearby, connected devices and allows adding them to be added as contacts.
 */
class ConnectionsActivity extends EnsichatActivity with OnItemClickListener {

  private lazy val database = new Database(this)

  private lazy val adapter = new UsersAdapter(this)

  /**
   * Initializes layout, registers connection and message listeners.
   */
  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)
    getSupportActionBar.setDisplayHomeAsUpEnabled(true)

    setContentView(R.layout.activity_connections)
    val list = findViewById(android.R.id.list).asInstanceOf[ListView]
    list.setAdapter(adapter)
    list.setOnItemClickListener(this)
    list.setEmptyView(findViewById(android.R.id.empty))

    val filter = new IntentFilter()
    filter.addAction(CallbackHandler.ActionConnectionsChanged)
    filter.addAction(Database.ActionContactsUpdated)
    LocalBroadcastManager.getInstance(this)
      .registerReceiver(onContactsUpdatedReceiver, filter)
  }

  override def onResume(): Unit = {
    super.onResume()
    runOnServiceConnected(() => {
      updateConnections()
    })
  }

  override def onDestroy(): Unit = {
    super.onDestroy()
    LocalBroadcastManager.getInstance(this).unregisterReceiver(onContactsUpdatedReceiver)
  }

  /**
   * Initiates adding the device as contact if it hasn't been added yet.
   */
  override def onItemClick(parent: AdapterView[_], view: View, position: Int, id: Long): Unit = {
    val contact = adapter.getItem(position)
    if (database.getContacts.contains(contact)) {
      val text = getString(R.string.contact_already_added, contact.name)
      Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
      return
    }

    new Builder(this)
      .setMessage(getString(R.string.dialog_add_contact, contact.name))
      .setPositiveButton(android.R.string.yes, new OnClickListener {
        override def onClick(dialog: DialogInterface, which: Int): Unit = {
          database.addContact(contact)
          Toast.makeText(ConnectionsActivity.this, R.string.toast_contact_added, Toast.LENGTH_SHORT)
            .show()
        }
      })
      .setNegativeButton(android.R.string.no, null)
      .show()
  }

  override def onOptionsItemSelected(item: MenuItem): Boolean = item.getItemId match {
    case android.R.id.home =>
      NavUtils.navigateUpFromSameTask(this)
      true
    case _ =>
      super.onOptionsItemSelected(item);
  }

  /**
   * Fetches connections and displays them (excluding contacts).
   */
  private val onContactsUpdatedReceiver = new BroadcastReceiver() {
    override def onReceive(context: Context, intent: Intent): Unit = {
      runOnUiThread(new Runnable {
        override def run(): Unit = updateConnections()
      })
    }
  }

  private def updateConnections(): Unit = {
    adapter.clear()
    service.get.connections().map(a => service.get.getUser(a))
      .foreach(adapter.add)
  }

}
