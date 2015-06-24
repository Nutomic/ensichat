package com.nutomic.ensichat.util

import android.content.Context
import android.view.{LayoutInflater, View, ViewGroup}
import android.widget.{ArrayAdapter, TextView}
import com.nutomic.ensichat.bluetooth.Device
import com.nutomic.ensichat.protocol.User
import com.nutomic.ensichat.protocol.body.Text
import com.nutomic.ensichat.R

/**
 * Displays [[Device]]s in ListView.
 */
class UsersAdapter(context: Context) extends
  ArrayAdapter[User](context, 0) {

  private lazy val database = new Database(context)

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
    database.getMessages(item.address, 1)
      .map(_.body)
      .foreach {
        case m: Text => summary.setText(m.text)
    }
    view
  }

}
