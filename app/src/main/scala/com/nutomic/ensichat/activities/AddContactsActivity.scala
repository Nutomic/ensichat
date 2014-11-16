package com.nutomic.ensichat.activities

import java.util.Date

import android.app.AlertDialog
import android.content.DialogInterface
import android.content.DialogInterface.OnClickListener
import android.os.Bundle
import android.view._
import android.widget.AdapterView.OnItemClickListener
import android.widget.{AdapterView, ListView, Toast}
import com.nutomic.ensichat.R
import com.nutomic.ensichat.bluetooth.ChatService.OnMessageReceivedListener
import com.nutomic.ensichat.bluetooth.{ChatService, Device}
import com.nutomic.ensichat.messages.{Message, RequestAddContactMessage, ResultAddContactMessage}
import com.nutomic.ensichat.util.DevicesAdapter

import scala.collection.SortedSet

/**
 * Lists all nearby, connected devices and allows adding them to contacts.
 *
 * Adding a contact requires confirmation on both sides.
 */
class AddContactsActivity extends EnsiChatActivity with ChatService.OnConnectionChangedListener
  with OnItemClickListener with OnMessageReceivedListener {

  private lazy val Adapter = new DevicesAdapter(this)

  private lazy val Database = service.database

  /**
   * Map of devices that should be added.
   */
  private var currentlyAdding = Map[Device.ID, AddContactInfo]()
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

    setContentView(R.layout.activity_add_contacts)
    val list = findViewById(android.R.id.list).asInstanceOf[ListView]
    list.setAdapter(Adapter)
    list.setOnItemClickListener(this)

    runOnServiceConnected(() => {
      service.registerConnectionListener(AddContactsActivity.this)
      service.registerMessageListener(this)
    })
  }

  /**
   * Displays newly connected devices in the list.
   */
  override def onConnectionChanged(devices: Map[Device.ID, Device]): Unit = {
    val filtered = devices.filter{ case (_, d) => d.Connected }
    runOnUiThread(new Runnable {
      override def run(): Unit  = {
        Adapter.clear()
        filtered.values.foreach(f => Adapter.add(f))
      }
    })
  }

  /**
   * Initiates adding the device as contact if it hasn't been added yet.
   */
  override def onItemClick(parent: AdapterView[_], view: View, position: Int, id: Long): Unit = {
    val device = Adapter.getItem(position)
    if (Database.isContact(device.Id)) {
      Toast.makeText(this, R.string.contact_already_added, Toast.LENGTH_SHORT).show()
      return
    }

    service.send(new RequestAddContactMessage(service.localDeviceId, device.Id, new Date()))
    addDeviceDialog(device)
  }

  /**
   * Shows a dialog to accept/deny adding a device as a new contact.
   */
  private def addDeviceDialog(device: Device): Unit = {
    val id = device.Id
    // Listener for dialog button clicks.
    val onClick = new OnClickListener {
      override def onClick(dialogInterface: DialogInterface, i: Int): Unit = i match {
        case DialogInterface.BUTTON_POSITIVE =>
          // Local user accepted contact, update state and send info to other device.
          currentlyAdding += (id -> new AddContactInfo(currentlyAdding(id).localConfirmed, true))
          addContactIfBothConfirmed(device)
          service.send(
            new ResultAddContactMessage(service.localDeviceId, device.Id, new Date(), true))
        case DialogInterface.BUTTON_NEGATIVE =>
          // Local user denied adding contact, send info to other device.
          service.send(
            new ResultAddContactMessage(service.localDeviceId, device.Id, new Date(), false))
      }
    }

    new AlertDialog.Builder(this)
      .setTitle(getString(R.string.add_contact_dialog, device.Name))
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
    messages.foreach(msg => {
      if (msg.receiver == service.localDeviceId) {
        msg match {
        case _: ResultAddContactMessage =>
          // Remote device wants to add us as a contact, show dialog.
          val sender = getDevice(msg.sender)
          addDeviceDialog(sender)
        case m: ResultAddContactMessage =>
          if (m.Accepted) {
            // Remote device accepted us as a contact, update state.
            currentlyAdding += (m.sender ->
              new AddContactInfo(true, currentlyAdding(m.sender).remoteConfirmed))
            addContactIfBothConfirmed(getDevice(m.sender))
          } else {
            // Remote device denied us as a contact, show a toast
            // and remove from [[currentlyAdding]].
            Toast.makeText(this, R.string.contact_not_added, Toast.LENGTH_LONG).show()
            currentlyAdding -= m.sender
          }
        case _ =>
        }
      }
    })
  }

  /**
   * Returns the [[Device]] for a given [[Device.ID]] that is stored in the [[Adapter]].
   */
  private def getDevice(id: Device.ID): Device = {
    // ArrayAdapter does not return the underlying array so we have to access it manually.
    for (i <- 0 until Adapter.getCount) {
      if (Adapter.getItem(i).Id == id) {
        return Adapter.getItem(i)
      }
    }
    throw new RuntimeException("Device to add was not found")
  }

  /**
   * Add the given device to contacts if [[AddContactInfo.localConfirmed]] and
   * [[AddContactInfo.remoteConfirmed]] are true for it in [[currentlyAdding]].
   */
  private def addContactIfBothConfirmed(device: Device): Unit = {
    val info = currentlyAdding(device.Id)
    if (info.localConfirmed && info.remoteConfirmed) {
      Database.addContact(device)
      Toast.makeText(this, getString(R.string.contact_added, device.Name), Toast.LENGTH_SHORT)
        .show()
    }
    currentlyAdding -= device.Id
  }

}
