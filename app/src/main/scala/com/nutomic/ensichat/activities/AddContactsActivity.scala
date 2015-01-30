package com.nutomic.ensichat.activities

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
import com.nutomic.ensichat.protocol.messages.{Message, RequestAddContact, ResultAddContact}
import com.nutomic.ensichat.protocol.{User, Address, ChatService, Crypto}
import com.nutomic.ensichat.util.{Database, UsersAdapter, IdenticonGenerator}

import scala.collection.SortedSet

/**
 * Lists all nearby, connected devices and allows adding them to contacts.
 *
 * Adding a contact requires confirmation on both sides.
 */
class AddContactsActivity extends EnsiChatActivity with ChatService.OnConnectionsChangedListener
  with OnItemClickListener with ChatService.OnMessageReceivedListener {

  private val Tag = "AddContactsActivity"

  private lazy val Adapter = new UsersAdapter(this)

  private lazy val Database = service.Database

  private lazy val Crypto = new Crypto(this)

  /**
   * Map of devices that should be added.
   */
  private var currentlyAdding = Map[User, AddContactInfo]()
    .withDefaultValue(new AddContactInfo(false, false))

  /**
   * Holds confirmation status for adding contacts.
   *
   * @param localConfirmed If true, the local user has accepted adding the contact.
   * @param remoteConfirmed If true, the remote contact has accepted adding this device as contact.
   */
  private case class AddContactInfo(localConfirmed: Boolean, remoteConfirmed: Boolean)

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
      Database.runOnContactsUpdated(updateList)
    })
  }

  override def onConnectionsChanged() = updateList()

  /**
   * Initiates adding the device as contact if it hasn't been added yet.
   */
  override def onItemClick(parent: AdapterView[_], view: View, position: Int, id: Long): Unit = {
    val contact = Adapter.getItem(position)
    service.sendTo(contact.Address, new RequestAddContact())
    addDeviceDialog(contact)
  }

  /**
   * Shows a dialog to accept/deny adding a device as a new contact.
   */
  private def addDeviceDialog(contact: User): Unit = {
    // Listener for dialog button clicks.
    val onClick = new OnClickListener {
      override def onClick(dialogInterface: DialogInterface, i: Int): Unit = i match {
        case DialogInterface.BUTTON_POSITIVE =>
          // Local user accepted contact, update state and send info to other device.
          currentlyAdding +=
            (contact -> new AddContactInfo(currentlyAdding(contact).localConfirmed, true))
          addContactIfBothConfirmed(contact)
          service.sendTo(contact.Address, new ResultAddContact(true))
        case DialogInterface.BUTTON_NEGATIVE =>
          // Local user denied adding contact, send info to other device.
          service.sendTo(contact.Address, new ResultAddContact(false))
      }
    }

    val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE).asInstanceOf[LayoutInflater]
    val view = inflater.inflate(R.layout.dialog_add_contact, null)

    val local = view.findViewById(R.id.local_identicon).asInstanceOf[ImageView]
    local.setImageBitmap(
      IdenticonGenerator.generate(Crypto.getLocalAddress, (150, 150), this))
    val remoteTitle = view.findViewById(R.id.remote_identicon_title).asInstanceOf[TextView]
    remoteTitle.setText(getString(R.string.remote_fingerprint_title, contact.Name))
    val remote = view.findViewById(R.id.remote_identicon).asInstanceOf[ImageView]
    remote.setImageBitmap(IdenticonGenerator.generate(contact.Address, (150, 150), this))

    new AlertDialog.Builder(this)
      .setTitle(getString(R.string.add_contact_dialog, contact.Name))
      .setView(view)
      .setPositiveButton(android.R.string.yes, onClick)
      .setNegativeButton(android.R.string.no, onClick)
      .show()
  }

  /**
   * Handles incoming [[RequestAddContact]] and [[ResultAddContact]] messages.
   *
   * These are only handled here and require user action, so contacts can only be added if
   * the user is in this activity.
   */
  override def onMessageReceived(msg: Message): Unit = {
    if (msg.Header.Target != Crypto.getLocalAddress)
      return

    msg.Body match {
      case _: RequestAddContact =>
        Log.i(Tag, "Remote device " + msg.Header.Origin + " wants to add us as a contact, showing dialog")
        service.getConnections.find(_.Address == msg.Header.Origin).foreach(addDeviceDialog)
      case _: ResultAddContact =>
        currentlyAdding.keys.find(_.Address == msg.Header.Origin).foreach(contact =>
          if (msg.Body.asInstanceOf[ResultAddContact].Accepted) {
            Log.i(Tag, contact.toString + " accepted us as a contact, updating state")
            currentlyAdding += (contact ->
              new AddContactInfo(true, currentlyAdding(contact).remoteConfirmed))
            addContactIfBothConfirmed(contact)
          } else {
            Log.i(Tag, contact.toString + " denied us as a contact, showing toast")
            Toast.makeText(this, R.string.contact_not_added, Toast.LENGTH_LONG).show()
            currentlyAdding -= contact
          }
          )
      case _ =>
    }
  }

  /**
   * Add the given device to contacts if [[AddContactInfo.localConfirmed]] and
   * [[AddContactInfo.remoteConfirmed]] are true for it in [[currentlyAdding]].
   */
  private def addContactIfBothConfirmed(contact: User): Unit = {
    val info = currentlyAdding(contact)
    if (info.localConfirmed && info.remoteConfirmed) {
      Log.i(Tag, "Adding new contact " + contact.toString)
      Database.addContact(contact)
      Toast.makeText(this, getString(R.string.contact_added, contact.Name), Toast.LENGTH_SHORT)
        .show()
      currentlyAdding -= contact
    }
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
  private def updateList(): Unit ={
    runOnUiThread(new Runnable {
      override def run(): Unit  = {
        Adapter.clear()
        (service.getConnections -- service.Database.getContacts).foreach(Adapter.add)
      }
    })
  }

}
