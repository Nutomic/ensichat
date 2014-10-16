package com.nutomic.ensichat.util

import android.content.Context
import android.view.{ViewGroup, View}
import android.widget.{TextView, ArrayAdapter}
import com.nutomic.ensichat.bluetooth.Device

class DevicesAdapter(context: Context) extends ArrayAdapter[Device](context, android.R.layout.simple_list_item_1) {

    override def getView(position: Int, convertView: View, parent: ViewGroup): View = {
      val view = super.getView(position, convertView, parent)
      val title: TextView = view.findViewById(android.R.id.text1).asInstanceOf[TextView]
      title.setText(getItem(position).name)
      return view
    }

}
