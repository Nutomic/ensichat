package com.nutomic.ensichat.activities

import android.content.Intent
import android.os.Bundle
import android.support.v4.app.NavUtils
import android.view._
import android.widget.AdapterView.OnItemClickListener
import android.widget._
import com.nutomic.ensichat.R
import com.nutomic.ensichat.protocol.{User, ChatService}
import com.nutomic.ensichat.protocol.messages.RequestAddContact
import com.nutomic.ensichat.util.Database.OnContactsUpdatedListener
import com.nutomic.ensichat.util.UsersAdapter

/**
 * Lists all nearby, connected devices and allows adding them to be added as contacts.
 */
class AddContactsActivity extends EnsiChatActivity with ChatService.OnConnectionsChangedListener
  with OnItemClickListener with OnContactsUpdatedListener {

  private val Tag = "AddContactsActivity"

  private lazy val adapter = new UsersAdapter(this)

  /**
   * Initializes layout, registers connection and message listeners.
   */
  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)
    getActionBar.setDisplayHomeAsUpEnabled(true)

    setContentView(R.layout.activity_add_contacts)
    val list = findViewById(android.R.id.list).asInstanceOf[ListView]
    list.setAdapter(adapter)
    list.setOnItemClickListener(this)
    list.setEmptyView(findViewById(android.R.id.empty))

    runOnServiceConnected(() => {
      service.registerConnectionListener(AddContactsActivity.this)
      service.database.runOnContactsUpdated(this)
    })
  }

  override def onConnectionsChanged() = onContactsUpdated()

  /**
   * Initiates adding the device as contact if it hasn't been added yet.
   */
  override def onItemClick(parent: AdapterView[_], view: View, position: Int, id: Long): Unit = {
    val contact = adapter.getItem(position)
    service.sendTo(contact.Address, new RequestAddContact())
    val intent = new Intent(this, classOf[ConfirmAddContactDialog])
    intent.putExtra(ConfirmAddContactDialog.ExtraContactAddress, contact.Address.toString)
    startActivity(intent)
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
  override def onContactsUpdated(): Unit ={
    runOnUiThread(new Runnable {
      override def run(): Unit  = {
        adapter.clear()
        (service.connections().map(a => service.getUser(a)) -- service.database.getContacts)
          .foreach(adapter.add)
      }
    })
  }

}
