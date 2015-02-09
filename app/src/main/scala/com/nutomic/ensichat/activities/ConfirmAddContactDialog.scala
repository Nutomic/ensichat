package com.nutomic.ensichat.activities

import android.app.AlertDialog
import android.content.DialogInterface.OnClickListener
import android.content.{Context, DialogInterface}
import android.os.Bundle
import android.util.Log
import android.view.{ContextThemeWrapper, LayoutInflater}
import android.widget.{ImageView, TextView, Toast}
import com.nutomic.ensichat.R
import com.nutomic.ensichat.protocol.ChatService.OnMessageReceivedListener
import com.nutomic.ensichat.protocol.messages.{Message, RequestAddContact, ResultAddContact}
import com.nutomic.ensichat.protocol.{Address, Crypto}
import com.nutomic.ensichat.util.IdenticonGenerator

object ConfirmAddContactDialog {

  val ExtraContactAddress = "contact_address"

}

/**
 * Shows a dialog for adding a new contact (including key fingerprints).
 */
class ConfirmAddContactDialog extends EnsiChatActivity with OnMessageReceivedListener
  with OnClickListener {

  private val Tag = "ConfirmAddContactDialog"

  private lazy val user = service.getUser(
    new Address(getIntent.getStringExtra(ConfirmAddContactDialog.ExtraContactAddress)))

  private var localConfirmed = false

  private var remoteConfirmed = false

  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)

    runOnServiceConnected(() => {
      showDialog()
      service.registerMessageListener(this)
    })
  }

  /**
   * Shows a dialog to accept/deny adding a device as a new contact.
   */
  private def showDialog(): Unit = {
    val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE).asInstanceOf[LayoutInflater]
    val view     = inflater.inflate(R.layout.dialog_add_contact, null)

    val local       = view.findViewById(R.id.local_identicon).asInstanceOf[ImageView]
    val remote      = view.findViewById(R.id.remote_identicon).asInstanceOf[ImageView]
    val remoteTitle = view.findViewById(R.id.remote_identicon_title).asInstanceOf[TextView]

    local.setImageBitmap(IdenticonGenerator.generate(Crypto.getLocalAddress(this), (150, 150), this))
    remote.setImageBitmap(IdenticonGenerator.generate(user.address, (150, 150), this))
    remoteTitle.setText(getString(R.string.remote_fingerprint_title, user.name))

    new AlertDialog.Builder(new ContextThemeWrapper(this, R.style.AppTheme))
      .setTitle(getString(R.string.add_contact_dialog, user.name))
      .setView(view)
      .setCancelable(false)
      .setPositiveButton(android.R.string.yes, this)
      .setNegativeButton(android.R.string.no, this)
      .setCancelable(false)
      .show()
  }

  override def onClick(dialogInterface: DialogInterface, i: Int): Unit = {
    val result = i match {
      case DialogInterface.BUTTON_POSITIVE =>
        localConfirmed = true
        addContactIfBothConfirmed()
        true
      case DialogInterface.BUTTON_NEGATIVE =>
        finish()
        false
    }
    service.sendTo(user.address, new ResultAddContact(result))
  }

  /**
   * Add the user to contacts if [[localConfirmed]] and [[remoteConfirmed]] are true.
   */
  private def addContactIfBothConfirmed(): Unit = {
    if (localConfirmed && remoteConfirmed) {
      Log.i(Tag, "Adding new contact " + user.toString)
      // Get the user again, in case it was updated in the mean time.
      service.database.addContact(service.getUser(user.address))
      Toast.makeText(this, getString(R.string.contact_added, user.name), Toast.LENGTH_SHORT)
        .show()
      finish()
    }
  }

  /**
   * Handles incoming [[RequestAddContact]] and [[ResultAddContact]] messages.
   *
   * These are only handled here and require user action, so contacts can only be added if
   * the user is in this activity.
   */
  override def onMessageReceived(msg: Message): Unit = {
    if (msg.Header.origin != user.address || msg.Header.target != Crypto.getLocalAddress(this))
      return

    msg.Body match {
      case m: ResultAddContact =>
        if (m.accepted) {
          Log.i(Tag, user.toString + " accepted us as a contact, updating state")
          remoteConfirmed = true
          addContactIfBothConfirmed()
        } else {
          Log.i(Tag, user.toString + " denied us as a contact, showing toast")
          Toast.makeText(this, R.string.contact_not_added, Toast.LENGTH_LONG).show()
          finish()
        }
      case _ =>
    }
  }

}
