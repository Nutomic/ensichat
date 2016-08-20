package com.nutomic.ensichat.fragments

import android.app.{AlertDialog, Dialog, DialogFragment}
import android.graphics.{Bitmap, Color}
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.{ImageView, TextView}
import com.google.zxing.BarcodeFormat
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.nutomic.ensichat.R
import com.nutomic.ensichat.core.routing.Address
import com.nutomic.ensichat.util.IdenticonGenerator

object UserInfoFragment {
  val ExtraAddress  = "address"
  val ExtraUserName = "user_name"
  val ExtraShowQr   = "show_qr"
}

/**
 * Displays identicon, username and address for a user.
 *
 * Use [[UserInfoFragment#getInstance]] to invoke.
 */
class UserInfoFragment extends DialogFragment {

  private lazy val address  = new Address(getArguments.getString(UserInfoFragment.ExtraAddress))
  private lazy val userName = getArguments.getString(UserInfoFragment.ExtraUserName)
  private lazy val showQr   = getArguments.getBoolean(UserInfoFragment.ExtraShowQr)

  override def onCreateDialog(savedInstanceState: Bundle): Dialog = {
    val view = LayoutInflater.from(getActivity).inflate(R.layout.fragment_identicon, null)

    view.findViewById(R.id.identicon)
      .asInstanceOf[ImageView]
      .setImageBitmap(IdenticonGenerator.generate(address, (150, 150), getActivity))
    view.findViewById(R.id.address)
      .asInstanceOf[TextView]
      .setText(getString(R.string.address_colon, address.toString()))

    if (showQr) {
      val matrix = new QRCodeWriter().encode(address.toString(), BarcodeFormat.QR_CODE, 150, 150)
      view.findViewById(R.id.qr_code)
        .asInstanceOf[ImageView]
        .setImageBitmap(renderMatrix(matrix))
    }

    new AlertDialog.Builder(getActivity)
      .setTitle(userName)
      .setView(view)
      .setPositiveButton(android.R.string.ok, null)
      .create()
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