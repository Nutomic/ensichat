package com.nutomic.ensichat.activities

import android.app.AlertDialog
import android.content.DialogInterface.OnClickListener
import android.content._
import android.os.Bundle
import android.support.v4.content.LocalBroadcastManager
import android.view.{ContextThemeWrapper, LayoutInflater}
import android.widget.{ImageView, TextView}
import com.nutomic.ensichat.R
import com.nutomic.ensichat.protocol.body.ResultAddContact
import com.nutomic.ensichat.protocol.{ChatService, Address, Crypto}
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

  private lazy val view = getSystemService(Context.LAYOUT_INFLATER_SERVICE)
    .asInstanceOf[LayoutInflater]
    .inflate(R.layout.dialog_add_contact, null)

  private lazy val dialog = new AlertDialog.Builder(new ContextThemeWrapper(this, R.style.AppTheme))
    .setTitle(getString(R.string.add_contact_dialog, user.name))
    .setView(view)
    .setPositiveButton(android.R.string.yes, this)
    .setNegativeButton(android.R.string.no, this)
    .setOnDismissListener(this)
    .create()

  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)

    runOnServiceConnected(() => showDialog())
    LocalBroadcastManager.getInstance(this)
      .registerReceiver(onConnectionsChangedReceiver,
                        new IntentFilter(ChatService.ActionConnectionsChanged))
  }

  override def onDestroy(): Unit = {
    super.onDestroy()
    LocalBroadcastManager.getInstance(this).unregisterReceiver(onConnectionsChangedReceiver)
  }

  /**
   * Shows a dialog to accept/deny adding a device as a new contact.
   */
  private def showDialog(): Unit = {
    val local       = view.findViewById(R.id.local_identicon).asInstanceOf[ImageView]
    val remote      = view.findViewById(R.id.remote_identicon).asInstanceOf[ImageView]
    val remoteTitle = view.findViewById(R.id.remote_identicon_title).asInstanceOf[TextView]

    val localAddress = new Crypto(this).localAddress
    local.setImageBitmap(IdenticonGenerator.generate(localAddress, (150, 150), this))
    remote.setImageBitmap(IdenticonGenerator.generate(user.address, (150, 150), this))
    remoteTitle.setText(getString(R.string.remote_fingerprint_title, user.name))

    dialog.show()
  }

  override def onClick(dialogInterface: DialogInterface, i: Int): Unit =
    service.sendTo(user.address, new ResultAddContact(i == DialogInterface.BUTTON_POSITIVE))

  override def onDismiss(dialog: DialogInterface): Unit = finish()

  private val onConnectionsChangedReceiver = new BroadcastReceiver {
    override def onReceive(context: Context, intent: Intent): Unit = {
      if (!service.connections().contains(user.address)) {
        dialog.dismiss()
        service.sendTo(user.address, new ResultAddContact(false))
        finish()
      }
    }
  }

}
