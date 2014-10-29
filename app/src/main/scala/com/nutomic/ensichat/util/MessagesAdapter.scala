package com.nutomic.ensichat.util

import android.content.Context
import android.view.{View, ViewGroup}
import android.widget.{ArrayAdapter, TextView}
import com.nutomic.ensichat.bluetooth.Device
import com.nutomic.ensichat.messages.TextMessage

/**
 * Displays [[TextMessage]]s in ListView.
 */
class MessagesAdapter(context: Context, localDevice: Device.ID) extends
  ArrayAdapter[TextMessage](context, android.R.layout.simple_list_item_1) {

  override def getView(position: Int, convertView: View, parent: ViewGroup): View = {
    val view: View = super.getView(position, convertView, parent)
    val tv: TextView = view.findViewById(android.R.id.text1).asInstanceOf[TextView]
    view.setBackgroundColor(context.getResources.getColor(
      if (getItem(position).sender == localDevice) android.R.color.holo_blue_light
      else android.R.color.holo_green_light))
    tv.setText(getItem(position).text)
    view
  }

}
