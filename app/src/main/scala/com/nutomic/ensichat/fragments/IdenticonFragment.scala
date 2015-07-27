package com.nutomic.ensichat.fragments

import android.app.{AlertDialog, Dialog, DialogFragment}
import android.os.Bundle
import android.view.{LayoutInflater, View, ViewGroup}
import android.widget.{ImageView, TextView}
import com.nutomic.ensichat.R
import com.nutomic.ensichat.protocol.Address
import com.nutomic.ensichat.util.IdenticonGenerator

object IdenticonFragment {
  private val ExtraAddress  = "address"
  private val ExtraUserName = "user_name"

  def getInstance(address: Address, userName: String): IdenticonFragment = {
    val bundle = new Bundle()
    bundle.putString(IdenticonFragment.ExtraAddress, address.toString)
    bundle.putString(IdenticonFragment.ExtraUserName, userName)

    val fragment = new IdenticonFragment()
    fragment.setArguments(bundle)
    fragment
  }
}

/**
 * Displays identicon, username and address for a user.
 *
 * Use [[IdenticonFragment#getInstance]] to invoke.
 */
class IdenticonFragment extends DialogFragment {

  private lazy val address = new Address(getArguments.getString(IdenticonFragment.ExtraAddress))
  private lazy val userName = getArguments.getString(IdenticonFragment.ExtraUserName)


  override def onCreateView(inflater: LayoutInflater, container: ViewGroup,
                          savedInstanceState: Bundle): View = {
    getDialog.setTitle(userName)
    val view = inflater.inflate(R.layout.activity_identicon, container)
    view.findViewById(R.id.identicon)
      .asInstanceOf[ImageView]
      .setImageBitmap(IdenticonGenerator.generate(address, (150, 150), getActivity))
    view.findViewById(R.id.address)
      .asInstanceOf[TextView]
      .setText(getString(R.string.address_colon, address.toString))
    view
  }

}