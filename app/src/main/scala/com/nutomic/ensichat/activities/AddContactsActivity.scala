package com.nutomic.ensichat.activities

import java.util.Date

import android.app.AlertDialog
import android.content.DialogInterface.OnClickListener
import android.content.{Context, DialogInterface}
import android.os.Bundle
import android.support.v4.app.NavUtils
import android.util.Log
import android.view._
import android.widget.AdapterView.OnItemClickListener
import android.widget._
import com.nutomic.ensichat.R
import com.nutomic.ensichat.aodvv2.Address
import com.nutomic.ensichat.bluetooth.ChatService
import com.nutomic.ensichat.bluetooth.ChatService.OnMessageReceivedListener
import com.nutomic.ensichat.messages.{Crypto, Message, RequestAddContactMessage, ResultAddContactMessage}
import com.nutomic.ensichat.util.{DevicesAdapter, IdenticonGenerator}

import scala.collection.SortedSet

/**
 * Lists all nearby, connected devices and allows adding them to contacts.
 *
 * Adding a contact requires confirmation on both sides.
 */
class AddContactsActivity extends EnsiChatActivity with ChatService.OnNearbyContactsChangedListener
  with OnItemClickListener with OnMessageReceivedListener {

  private val Tag = "AddContactsActivity"

  private lazy val Adapter = new DevicesAdapter(this)

  private lazy val Database = service.database

  private lazy val Crypto = new Crypto(this)

  /**
   * Map of devices that should be added.
   */
  private var currentlyAdding = Map[Address, AddContactInfo]()
    .withDefaultValue(new AddContactInfo(false, false))

  /**
   * Holds confirmation status for adding contacts.
   *
   * @param localConfirmed If true, the local user has accepted adding the contact.
   * @param remoteConfirmed If true, the remote contact has accepted adding this device as contact.
   */
  private class AddContactInfo(val localConfirmed: Boolean, val remoteConfirmed: Boolean) {
  }

  /**
   * Initializes layout, registers connection and message listeners.
   */
  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)
    getActionBar.setDisplayHomeAsUpEnabled(true)

    setContentView(R.layout.activity_add_contacts)
    val list = findViewById(android.R.id.list).asInstanceOf[ListView]
    list.setAdapter(Adapter)
    list.setOnItemClickListener(this)
    list.setEmptyView(findViewById(android.R.id.empty))

    runOnServiceConnected(() => {
      service.registerConnectionListener(AddContactsActivity.this)
      service.registerMessageListener(this)
    })
  }

  /**
   * Displays newly connected devices in the list.
   */
  override def onNearbyContactsChanged(devices: Set[Address]): Unit = {
    runOnUiThread(new Runnable {
      override def run(): Unit  = {
        Adapter.clear()
        devices.foreach(Adapter.add)
      }
    })
  }

  /**
   * Initiates adding the device as contact if it hasn't been added yet.
   */
  override def onItemClick(parent: AdapterView[_], view: View, position: Int, id: Long): Unit = {
    val address = Adapter.getItem(position)
    if (Database.isContact(address)) {
      Toast.makeText(this, R.string.contact_already_added, Toast.LENGTH_SHORT).show()
      return
    }

    service.send(new RequestAddContactMessage(Crypto.getLocalAddress, address, new Date()))
    addDeviceDialog(address)
  }

  /**
   * Shows a dialog to accept/deny adding a device as a new contact.
   */
  private def addDeviceDialog(address: Address): Unit = {
    // Listener for dialog button clicks.
    val onClick = new OnClickListener {
      override def onClick(dialogInterface: DialogInterface, i: Int): Unit = i match {
        case DialogInterface.BUTTON_POSITIVE =>
          // Local user accepted contact, update state and send info to other device.
          currentlyAdding +=
            (address -> new AddContactInfo(currentlyAdding(address).localConfirmed, true))
          addContactIfBothConfirmed(address)
          service.send(
            new ResultAddContactMessage(Crypto.getLocalAddress, address, new Date(), true))
        case DialogInterface.BUTTON_NEGATIVE =>
          // Local user denied adding contact, send info to other device.
          service.send(
            new ResultAddContactMessage(Crypto.getLocalAddress, address, new Date(), false))
      }
    }

    val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE).asInstanceOf[LayoutInflater]
    val view = inflater.inflate(R.layout.dialog_add_contact, null)

    val local = view.findViewById(R.id.local_identicon).asInstanceOf[ImageView]
    local.setImageBitmap(
      IdenticonGenerator.generate(Crypto.getLocalAddress, (150, 150), this))
    val remoteTitle = view.findViewById(R.id.remote_identicon_title).asInstanceOf[TextView]
    remoteTitle.setText(getString(R.string.remote_fingerprint_title, address))
    val remote = view.findViewById(R.id.remote_identicon).asInstanceOf[ImageView]
    remote.setImageBitmap(IdenticonGenerator.generate(address, (150, 150), this))

    new AlertDialog.Builder(this)
      .setTitle(getString(R.string.add_contact_dialog, address))
      .setView(view)
      .setPositiveButton(android.R.string.yes, onClick)
      .setNegativeButton(android.R.string.no, onClick)
      .show()
  }

  /**
   * Handles incoming [[RequestAddContactMessage]] and [[ResultAddContactMessage]] messages.
   *
   * These are only handled here and require user action, so contacts can only be added if
   * the user is in this activity.
   */
  override def onMessageReceived(messages: SortedSet[Message]): Unit = {
    messages.filter(_.receiver == Crypto.getLocalAddress)
      .foreach{
        case m: RequestAddContactMessage =>
          Log.i(Tag, "Remote device " + m.sender + " wants to add us as a contact, showing dialog")
          addDeviceDialog(m.sender)
        case m: ResultAddContactMessage =>
          if (m.Accepted) {
            Log.i(Tag, "Remote device " + m.sender + " accepted us as a contact, updating state")
            currentlyAdding += (m.sender ->
              new AddContactInfo(true, currentlyAdding(m.sender).remoteConfirmed))
            addContactIfBothConfirmed(m.sender)
          } else {
            Log.i(Tag, "Remote device " + m.sender + " denied us as a contact, showing toast")
            Toast.makeText(this, R.string.contact_not_added, Toast.LENGTH_LONG).show()
            currentlyAdding -= m.sender
          }
        case _ =>
      }
  }

  /**
   * Add the given device to contacts if [[AddContactInfo.localConfirmed]] and
   * [[AddContactInfo.remoteConfirmed]] are true for it in [[currentlyAdding]].
   */
  private def addContactIfBothConfirmed(address: Address): Unit = {
    val info = currentlyAdding(address)
    if (info.localConfirmed && info.remoteConfirmed) {
      Log.i(Tag, "Adding new contact " + address.toString)
      Database.addContact(address)
      Toast.makeText(this, getString(R.string.contact_added, address.toString), Toast.LENGTH_SHORT)
        .show()
      currentlyAdding -= address
    }
  }

  override def onOptionsItemSelected(item: MenuItem): Boolean = item.getItemId match {
    case android.R.id.home =>
      NavUtils.navigateUpFromSameTask(this)
      true;
    case _ =>
      super.onOptionsItemSelected(item);
  }

}
