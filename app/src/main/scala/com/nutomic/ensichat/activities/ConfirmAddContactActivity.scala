package com.nutomic.ensichat.activities

import android.app.AlertDialog
import android.content.DialogInterface.OnClickListener
import android.content.{Context, DialogInterface}
import android.os.Bundle
import android.view.{ContextThemeWrapper, LayoutInflater}
import android.widget.{ImageView, TextView}
import com.nutomic.ensichat.R
import com.nutomic.ensichat.protocol.body.ResultAddContact
import com.nutomic.ensichat.protocol.{Address, Crypto}
import com.nutomic.ensichat.util.IdenticonGenerator

object ConfirmAddContactActivity {

  val ExtraContactAddress = "contact_address"

}

/**
 * Shows a dialog for adding a new contact (including key fingerprints).
 */
class ConfirmAddContactActivity extends EnsichatActivity with OnClickListener
  with DialogInterface.OnDismissListener {

  private lazy val user = service.getUser(
    new Address(getIntent.getStringExtra(ConfirmAddContactActivity.ExtraContactAddress)))

  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)

    runOnServiceConnected(() => showDialog())
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

    val localAddress = new Crypto(this).localAddress
    local.setImageBitmap(IdenticonGenerator.generate(localAddress, (150, 150), this))
    remote.setImageBitmap(IdenticonGenerator.generate(user.address, (150, 150), this))
    remoteTitle.setText(getString(R.string.remote_fingerprint_title, user.name))

    new AlertDialog.Builder(new ContextThemeWrapper(this, R.style.AppTheme))
      .setTitle(getString(R.string.add_contact_dialog, user.name))
      .setView(view)
      .setPositiveButton(android.R.string.yes, this)
      .setNegativeButton(android.R.string.no, this)
      .setOnDismissListener(this)
      .show()
  }

  override def onClick(dialogInterface: DialogInterface, i: Int): Unit =
    service.sendTo(user.address, new ResultAddContact(i == DialogInterface.BUTTON_POSITIVE))

  override def onDismiss(dialog: DialogInterface): Unit = finish()

}
