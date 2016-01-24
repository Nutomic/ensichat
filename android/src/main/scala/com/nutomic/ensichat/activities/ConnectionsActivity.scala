package com.nutomic.ensichat.activities

import android.app.Activity
import android.app.AlertDialog.Builder
import android.content.DialogInterface.OnClickListener
import android.content._
import android.os.Bundle
import android.support.v4.app.NavUtils
import android.support.v4.content.LocalBroadcastManager
import android.view._
import android.widget.AdapterView.OnItemClickListener
import android.widget._
import com.google.zxing.integration.android.IntentIntegrator
import com.nutomic.ensichat.R
import com.nutomic.ensichat.core.Address
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

  override def onCreateOptionsMenu(menu: Menu): Boolean = {
    getMenuInflater.inflate(R.menu.connections, menu)
    true
  }

  override def onOptionsItemSelected(item: MenuItem): Boolean = item.getItemId match {
    case R.id.add_contact =>
        val et = new EditText(this)
        new Builder(this)
          .setTitle(R.string.enter_id)
          .setView(et)
          .setPositiveButton(android.R.string.ok, new OnClickListener {
            override def onClick(dialog: DialogInterface, which: Int): Unit = {
              addContact(et.getText.toString)
            }
          })
          .setNegativeButton(android.R.string.cancel, null)
          .show()
      true
    case R.id.scan_qr =>
      new IntentIntegrator(this).initiateScan
      true
    case android.R.id.home =>
      NavUtils.navigateUpFromSameTask(this)
      true
    case _ =>
      super.onOptionsItemSelected(item)
  }

  /**
   * Initiates adding the device as contact if it hasn't been added yet.
   */
  override def onItemClick(parent: AdapterView[_], view: View, position: Int, id: Long): Unit =
    addContact(adapter.getItem(position).address.toString)

  /**
   * Receives value of scanned QR code and sets it as device ID.
   */
  override def onActivityResult(requestCode: Int, resultCode: Int, intent: Intent) {
    val scanResult = IntentIntegrator.parseActivityResult(requestCode, resultCode, intent)
    if (scanResult != null && resultCode == Activity.RESULT_OK) {
      addContact(scanResult.getContents)
    }
  }

  /**
   * Parses the address, and shows a dialog to add the user as a contact.
   *
   * Displays a warning toast if the address is invalid or if the user is already a contact.
   */
  private def addContact(address: String): Unit = {
    val parsedAddress =
      try {
        new Address(address)
      } catch {
        case e: IllegalArgumentException =>
          Toast.makeText(this, R.string.invalid_address, Toast.LENGTH_LONG).show()
          return
      }

    val user = service.get.getUser(parsedAddress)

    if (database.getContacts.map(_.address).contains(user.address)) {
      val text = getString(R.string.contact_already_added, user.name)
      Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
      return
    }

    new Builder(this)
      .setMessage(getString(R.string.dialog_add_contact, user.name))
      .setPositiveButton(android.R.string.yes, new OnClickListener {
        override def onClick(dialog: DialogInterface, which: Int): Unit = {
          database.addContact(user)
          Toast.makeText(ConnectionsActivity.this, R.string.toast_contact_added, Toast.LENGTH_SHORT)
            .show()
        }
      })
      .setNegativeButton(android.R.string.no, null)
      .show()
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
