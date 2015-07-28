package com.nutomic.ensichat.views

import java.text.DateFormat

import android.content.Context
import android.view.{Gravity, LayoutInflater, View, ViewGroup}
import android.widget.{ArrayAdapter, LinearLayout, TextView}
import com.nutomic.ensichat.R
import com.nutomic.ensichat.protocol.body.Text
import com.nutomic.ensichat.protocol.header.ContentHeader
import com.nutomic.ensichat.protocol.{Address, Message}

/**
 * Displays [[Message]]s in ListView.
 */
class MessagesAdapter(context: Context, remoteAddress: Address) extends
  ArrayAdapter[Message](context, 0, 0) {

  /**
   * Free space to the right/left to a message depending on who sent it, in dip.
   */
  private val MessageMargin = 50

  override def getView(position: Int, convertView: View, parent: ViewGroup): View = {
    val view =
      if (convertView != null)
        convertView.asInstanceOf[LinearLayout]
      else
        context.getSystemService(Context.LAYOUT_INFLATER_SERVICE)
          .asInstanceOf[LayoutInflater]
          .inflate(R.layout.item_message, parent, false)
          .asInstanceOf[LinearLayout]
    val text = view.findViewById(R.id.text).asInstanceOf[TextView]
    val time = view.findViewById(R.id.time).asInstanceOf[TextView]

    val msg = getItem(position)
    text.setText(msg.body.asInstanceOf[Text].text)
    time.setText(DateFormat.getTimeInstance(DateFormat.SHORT).format(msg.header.asInstanceOf[ContentHeader].time))

    val lp = new LinearLayout.LayoutParams(view.getLayoutParams)
    val margin = (MessageMargin * context.getResources.getDisplayMetrics.density).toInt
    val container = view.findViewById(R.id.container).asInstanceOf[LinearLayout]
    if (getItem(position).header.origin != remoteAddress) {
      container.setGravity(Gravity.RIGHT)
      view.setGravity(Gravity.RIGHT)
      lp.setMargins(margin, 0, 0, 0)
    } else {
      container.setGravity(Gravity.LEFT)
      view.setGravity(Gravity.LEFT)
      lp.setMargins(0, 0, margin, 0)
    }
    view.setLayoutParams(lp)

    view
  }

}
