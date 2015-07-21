package com.nutomic.ensichat.util

import android.content.Context
import android.view.{LayoutInflater, View, ViewGroup}
import android.widget.{ArrayAdapter, TextView}
import com.nutomic.ensichat.R
import com.nutomic.ensichat.bluetooth.Device
import com.nutomic.ensichat.protocol.User

/**
 * Displays [[Device]]s in ListView.
 */
class UsersAdapter(context: Context) extends
  ArrayAdapter[User](context, 0) {

  override def getView(position: Int, convertView: View, parent: ViewGroup): View = {
    val view =
      if (convertView == null) {
        context.getSystemService(Context.LAYOUT_INFLATER_SERVICE)
          .asInstanceOf[LayoutInflater]
          .inflate(R.layout.item_user, parent, false)
      } else
        convertView
    val title = view.findViewById(android.R.id.text1).asInstanceOf[TextView]
    val summary = view.findViewById(android.R.id.text2).asInstanceOf[TextView]
    val item = getItem(position)
    title.setText(item.name)
    summary.setText(item.status)
    view
  }

}
