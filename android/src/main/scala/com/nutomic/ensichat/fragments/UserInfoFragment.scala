package com.nutomic.ensichat.fragments

import android.app.{AlertDialog, Dialog, DialogFragment}
import android.content.{ClipData, ClipboardManager, Context}
import android.graphics.{Bitmap, Color}
import android.os.Bundle
import android.view.View.{OnClickListener, OnLongClickListener}
import android.view.{LayoutInflater, View}
import android.widget.{ImageView, TextView, Toast}
import com.google.zxing.BarcodeFormat
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.nutomic.ensichat.R
import com.nutomic.ensichat.core.routing.Address
import com.nutomic.ensichat.util.IdenticonGenerator

object UserInfoFragment {
  val ExtraAddress  = "address"
  val ExtraUserName = "user_name"
}

/**
 * Displays identicon, username and address for a user.
 *
 * Use [[UserInfoFragment#getInstance]] to invoke.
 */
class UserInfoFragment extends DialogFragment with OnLongClickListener {

  private lazy val address  = new Address(getArguments.getString(UserInfoFragment.ExtraAddress))
  private lazy val userName = getArguments.getString(UserInfoFragment.ExtraUserName)

  override def onCreateDialog(savedInstanceState: Bundle): Dialog = {
    val view = LayoutInflater.from(getActivity).inflate(R.layout.fragment_identicon, null)

    view.findViewById(R.id.identicon)
      .asInstanceOf[ImageView]
      .setImageBitmap(IdenticonGenerator.generate(address, (150, 150), getActivity))
    val addressTextView = view.findViewById(R.id.address)
      .asInstanceOf[TextView]
    addressTextView.setText(getString(R.string.address_colon, address.toString()))
    addressTextView.setOnLongClickListener(this)
    addressTextView.setOnClickListener(new OnClickListener {
      override def onClick(v: View): Unit = onLongClick(v)
    })

    val matrix = new QRCodeWriter().encode(address.toString(), BarcodeFormat.QR_CODE, 150, 150)
    view.findViewById(R.id.qr_code)
      .asInstanceOf[ImageView]
      .setImageBitmap(renderMatrix(matrix))

    new AlertDialog.Builder(getActivity)
      .setTitle(userName)
      .setView(view)
      .setPositiveButton(android.R.string.ok, null)
      .create()
  }

  override def onLongClick(v: View): Boolean = {
    val cm = getContext.getSystemService(Context.CLIPBOARD_SERVICE).asInstanceOf[ClipboardManager]
    val clip = ClipData.newPlainText(getContext.getString(R.string.ensichat_user_address), address.toString)
    cm.setPrimaryClip(clip)
    Toast.makeText(getContext, R.string.address_copied_to_clipboard, Toast.LENGTH_SHORT).show()
    true
  }

  /**
   * Converts a [[BitMatrix]] instance into a [[Bitmap]].
   */
  private def renderMatrix(bitMatrix: BitMatrix): Bitmap = {
    val height = bitMatrix.getHeight
    val width = bitMatrix.getWidth
    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
    for (x <- 0 until width) {
      for (y <- 0 until height) {
        val color =
          if (bitMatrix.get(x,y))
            Color.BLACK
          else
            Color.WHITE
        bmp.setPixel(x, y, color)
      }
    }
    bmp
  }

}