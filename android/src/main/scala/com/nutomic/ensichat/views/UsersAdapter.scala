package com.nutomic.ensichat.views

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.view.View.OnClickListener
import android.view.{LayoutInflater, View, ViewGroup}
import android.widget.{ArrayAdapter, ImageView, TextView}
import com.nutomic.ensichat.R
import com.nutomic.ensichat.core.User
import com.nutomic.ensichat.fragments.IdenticonFragment
import com.nutomic.ensichat.util.IdenticonGenerator

/**
 * Displays [[User]]s in ListView.
 */
class UsersAdapter(activity: Activity) extends ArrayAdapter[User](activity, 0) with OnClickListener {

  override def getView(position: Int, convertView: View, parent: ViewGroup): View = {
    val view =
      if (convertView == null) {
        activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE)
          .asInstanceOf[LayoutInflater]
          .inflate(R.layout.item_user, parent, false)
      } else
        convertView

    val identicon = view.findViewById(R.id.identicon).asInstanceOf[ImageView]
    val title     = view.findViewById(android.R.id.text1).asInstanceOf[TextView]
    val summary   = view.findViewById(android.R.id.text2).asInstanceOf[TextView]

    val user = getItem(position)
    identicon.setImageBitmap(IdenticonGenerator.generate(user.address, (50, 50), activity))
    identicon.setOnClickListener(this)
    identicon.setTag(user)
    title.setText(user.name)
    summary.setText(user.status)
    view
  }

  override def onClick (v: View): Unit = {
    val user = v.getTag.asInstanceOf[User]
    val fragment = new IdenticonFragment()
    val bundle = new Bundle()
    bundle.putString(IdenticonFragment.ExtraAddress, user.address.toString)
    bundle.putString(IdenticonFragment.ExtraUserName, user.name)
    fragment.setArguments(bundle)
    fragment.show(activity.getFragmentManager, "dialog")
  }

}
